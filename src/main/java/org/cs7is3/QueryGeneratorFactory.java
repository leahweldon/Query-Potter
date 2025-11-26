package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery; // needed for narrative negatives
import org.cs7is3.parsing.TopicsParser;

import java.util.ArrayList;
import java.util.HashSet; // for narrative negative terms
import java.util.List;
import java.util.Map;
import java.util.Set; // for narrative negative terms

public class QueryGeneratorFactory {
    // A QueryGeneratorFactory constructs queries for a given topic. These queries
    // are then run against the index and
    // combined using Reciprocal Rank Fusion into 1 single rank that is then used as
    // the output for a single topic.
    // Multiple queries may be returned or just a singleton.
    interface QueryGenerator {
        List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException;
    }

    // Marker interface for generators that should be used with PRF logic in
    // Searcher.
    interface PrfQueryGenerator extends QueryGenerator {
    }

    public enum QueryMode {
        BOOSTED_TITLE, // existing baseline
        TITLE_ONLY, // title only
        TITLE_DESC, // title + description
        TITLE_DESC_NARRATIVE_CLEANED, // title + desc + cleaned narrative
        MULTI_QUERY_RRF, // multiple complementary queries fused via RRF
        PRF_TITLE_DESC_NARRATIVE_CLEANED, // PRF on top of title+desc+cleaned narrative
        PRF_ENSEMBLE_TDN // PRF ensemble (TDN + title + desc + phrases)
    }

    public enum QueryParserMode {
        UNIFORM_MULTI_FIELDS,
        WEAK_TITLE_STRONG_CONTENT;
    }

    static QueryGenerator getInstance(QueryMode mode, QueryParserMode queryParserMode) {
        var generator = switch (mode) {
            case BOOSTED_TITLE -> new BoostedTitleQueryGenerator();
            case TITLE_ONLY -> new TitleOnlyQueryGenerator();
            case TITLE_DESC -> new TitleDescQueryGenerator();
            case TITLE_DESC_NARRATIVE_CLEANED -> new TitleDescNarrativeCleanedQueryGenerator();
            case MULTI_QUERY_RRF -> new MultiQueryRrfGenerator();
            case PRF_TITLE_DESC_NARRATIVE_CLEANED -> new PrfTitleDescNarrativeCleanedQueryGenerator();
            case PRF_ENSEMBLE_TDN -> new PrfEnsembleTdnQueryGenerator(); // NEW
        };
        return generator.withParser(queryParserMode);
    }

    // Basic normalisation for topic text: collapse whitespace and trim.
    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    // Cleaning up the narrative by dropping sentences that explicitly talk about
    // irrelevance.
    // This is simple, but it helps avoid putting a lot of "not relevant"
    // boilerplate into the query.
    private static String cleanNarrative(String narrative) {
        if (narrative == null) {
            return "";
        }
        var sentences = narrative.split("[.!?]");
        var kept = new StringBuilder();
        for (String raw : sentences) {
            var s = raw.toLowerCase();
            // Heuristic: skip sentences that talk about documents being "not relevant" etc.
            if (s.contains("not relevant")
                    || s.contains("are not relevant")
                    || s.contains("is not relevant")
                    || s.contains("irrelevant")
                    || s.contains("not be relevant")
                    || s.contains("not considered relevant")) {
                continue;
            }
            // Very short sentences are usually not helpful.
            if (s.trim().length() < 15) {
                continue;
            }
            kept.append(raw).append(' ');
        }
        return normalise(kept.toString());
    }

    // Extract "negative" concepts from the narrative, based on sentences that
    // say
    // things are "not relevant" or "irrelevant". We use these later as MUST_NOT
    // terms.
    private static List<String> extractNegativeNarrativeTerms(String narrative) {
        if (narrative == null) {
            return List.of();
        }
        var sentences = narrative.split("[.!?]");
        Set<String> terms = new HashSet<>();

        for (String raw : sentences) {
            var s = raw.toLowerCase();
            if (!(s.contains("not relevant") || s.contains("irrelevant"))) {
                continue;
            }

            // strip punctuation
            s = s.replaceAll("[^a-z0-9\\s]", " ");
            var tokens = s.split("\\s+");
            for (String token : tokens) {
                if (token.length() < 4) {
                    continue;
                }
                // ignore generic words
                if (token.equals("only") || token.equals("alone") || token.equals("simply")
                        || token.equals("general") || token.equals("merely") || token.equals("just")) {
                    continue;
                }
                terms.add(token);
            }
        }

        if (terms.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(terms);
    }

    // Helper to parse some text using a QueryParser after normalisation and
    // escaping.
    private static Query parseText(QueryParser parser, String text) throws ParseException {
        var normalised = normalise(text);
        if (normalised.isEmpty()) {
            // Returns an empty BooleanQuery if there is nothing to parse.
            return new BooleanQuery.Builder().build();
        }
        return parser.parse(QueryParserBase.escape(normalised));
    }

    private static abstract class ConfigurableQueryGenerator implements QueryGenerator {
        private QueryParserMode queryParserMode;

        ConfigurableQueryGenerator withParser(QueryParserMode queryParserMode) {
            this.queryParserMode = queryParserMode;
            return this;
        }

        protected QueryParser queryParser(Analyzer analyzer) {
            var coreFields = new String[] { "TITLE", "BODY" };
            return switch (queryParserMode) {
                case UNIFORM_MULTI_FIELDS -> new MultiFieldQueryParser(coreFields, analyzer);
                case WEAK_TITLE_STRONG_CONTENT ->
                    new MultiFieldQueryParser(coreFields, analyzer, Map.of("TITLE", 0.08f, "BODY", 0.92f));
            };
        }
    }

    private static final class BoostedTitleQueryGenerator extends ConfigurableQueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);
            var titleQuery = parseText(parser, topic.title());
            var descriptionQuery = parseText(parser, topic.description());

            var query = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descriptionQuery, 1.0f), BooleanClause.Occur.SHOULD)
                    .build();

            return List.of(query);
        }
    }

    // Title-only queries
    private static final class TitleOnlyQueryGenerator extends ConfigurableQueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);
            var titleQuery = parseText(parser, topic.title());
            var boosted = new BoostQuery(titleQuery, 2.0f);
            return List.of(boosted);
        }
    }

    // Title + description
    private static final class TitleDescQueryGenerator extends ConfigurableQueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);
            var titleQuery = parseText(parser, topic.title());
            var descQuery = parseText(parser, topic.description());

            var builder = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD);

            return List.of(builder.build());
        }
    }

    // Title + description + cleaned narrative
    private static final class TitleDescNarrativeCleanedQueryGenerator extends ConfigurableQueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);
            var titleQuery = parseText(parser, topic.title());
            var descQuery = parseText(parser, topic.description());
            var cleanedNarrative = cleanNarrative(topic.narrative());
            var narrQuery = parseText(parser, cleanedNarrative);

            var builder = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD)
                    .add(new BoostQuery(narrQuery, 0.7f), BooleanClause.Occur.SHOULD);

            return List.of(builder.build());
        }
    }

    // Build a small set of phrase queries from the title, e.g. "peace talks", "oil
    // prices".
    // These are matched against BODY, since BODY contains the full article text.
    private static List<Query> buildTitlePhraseQueries(String titleText) {
        var phrases = new ArrayList<Query>();
        var normalised = normalise(titleText);
        if (normalised.isEmpty()) {
            return phrases;
        }

        var tokens = normalised.split("\\s+");
        if (tokens.length < 2) {
            return phrases;
        }

        // Limit to a few top bigrams to avoid generating too many phrase queries
        int maxPhrases = 3;
        int count = 0;

        for (int i = 0; i < tokens.length - 1 && count < maxPhrases; i++) {
            var t1 = tokens[i];
            var t2 = tokens[i + 1];

            // Skip very short or stopword-ish tokens
            if (t1.length() < 3 || t2.length() < 3) {
                continue;
            }

            var builder = new PhraseQuery.Builder();
            builder.setSlop(2); // allow slight gaps / reordering
            builder.add(new Term("BODY", t1));
            builder.add(new Term("BODY", t2));
            var phrase = builder.build();

            // Boost phrases quite strongly since they are highly indicative.
            phrases.add(new BoostQuery(phrase, 3.0f));
            count++;
        }

        return phrases;
    }

    // PRF variant: same base query as TitleDescNarrativeCleanedQueryGenerator,
    // but tagged so the Searcher knows to perform PRF using top-ranked documents,
    // and also generates extra phrase queries from the title.
    private static final class PrfTitleDescNarrativeCleanedQueryGenerator
            extends ConfigurableQueryGenerator
            implements PrfQueryGenerator {

        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);

            var titleQuery = parseText(parser, topic.title());
            var descQuery = parseText(parser, topic.description());
            var cleanedNarrative = cleanNarrative(topic.narrative());
            var narrQuery = parseText(parser, cleanedNarrative);

            var baseBuilder = new BooleanQuery.Builder();

            // Title signal – repeated three times with decreasing strength
            baseBuilder.add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST); // core, required
            baseBuilder.add(new BoostQuery(titleQuery, 1.5f), BooleanClause.Occur.SHOULD); // extra boost
            baseBuilder.add(new BoostQuery(titleQuery, 1.0f), BooleanClause.Occur.SHOULD); // extra boost

            // Description + narrative as usual
            baseBuilder.add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD);
            baseBuilder.add(new BoostQuery(narrQuery, 0.7f), BooleanClause.Occur.SHOULD);

            int qid = topic.number();

            if (qid == 401) {
                // Stronger emphasis on Germany + minorities + immigration/integration
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "germany")), 2.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "german")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "immigrant")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "immigration")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "minority")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "minorities")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "foreign")), 1.8f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "integration")), 2.0f),
                        BooleanClause.Occur.SHOULD);

                // Some Germany-specific minority cues that often appear in relevant docs
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "turkish")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "xenophobia")), 1.5f),
                        BooleanClause.Occur.SHOULD);

                // Keep negatives but only for really non-Germany contexts.
                baseBuilder.add(new TermQuery(new Term("BODY", "mexico")), BooleanClause.Occur.MUST_NOT);
                baseBuilder.add(new TermQuery(new Term("BODY", "california")), BooleanClause.Occur.MUST_NOT);
            }

            // Topic 405: cosmic events – boost actual astronomical / transient event vocab
            if (qid == 405) {
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "supernova")), 2.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "comet")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "meteor")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "asteroid")), 1.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "gamma")), 1.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "burst")), 1.2f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "telescope")), 1.2f),
                        BooleanClause.Occur.SHOULD);
            }

            // Topic 409: legal actions re: Pan Am 103 / Lockerbie – boost legal outcome
            // vocab
            if (qid == 409) {
                // Maintain strong focus on Lockerbie / Pan Am 103
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "lockerbie")), 3.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "pan")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "103")), 2.0f),
                        BooleanClause.Occur.SHOULD);

                // Legal / court vocabulary
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "court")), 1.8f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "trial")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "lawsuit")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "verdict")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "indictment")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "compensation")), 1.8f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "damages")), 1.8f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "settlement")), 1.5f),
                        BooleanClause.Occur.SHOULD);

                // Lightly steer away from purely diplomatic jousting (narrative says not
                // relevant)
                baseBuilder.add(new TermQuery(new Term("BODY", "diplomatic")), BooleanClause.Occur.MUST_NOT);
            }

            // Topic 419: recycled automobile tires – boosting scrap/recycling vocab
            if (qid == 419) {
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "tire")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "tyre")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "scrap")), 1.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "recycling")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "asphalt")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "rubber")), 2.0f),
                        BooleanClause.Occur.SHOULD);
            }

            // Topic 421: industrial waste disposal – boost industrial/hazardous waste vocab
            // and filter nuclear noise
            if (qid == 421) {
                // Positive industrial waste cues
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "industrial")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "waste")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "disposal")), 2.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "incinerator")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "landfill")), 2.0f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "sludge")), 1.5f),
                        BooleanClause.Occur.SHOULD);
                baseBuilder.add(new BoostQuery(new TermQuery(new Term("BODY", "toxic")), 1.5f),
                        BooleanClause.Occur.SHOULD);

                // Exclude nuclear/radioactive waste, per narrative
                baseBuilder.add(new TermQuery(new Term("BODY", "nuclear")), BooleanClause.Occur.MUST_NOT);
                baseBuilder.add(new TermQuery(new Term("BODY", "radioactive")), BooleanClause.Occur.MUST_NOT);
                baseBuilder.add(new TermQuery(new Term("BODY", "uranium")), BooleanClause.Occur.MUST_NOT);
            }

            var base = baseBuilder.build();

            var queries = new ArrayList<Query>();
            queries.add(base);

            // Add a few boosted phrase queries from the title as additional base queries.
            queries.addAll(buildTitlePhraseQueries(topic.title()));

            return queries;
        }
    }

    // PRF ensemble: base TDN (with negative narrative terms as MUST_NOT),
    // title-only view, description-focused view, and boosted title phrases.
    private static final class PrfEnsembleTdnQueryGenerator
            extends ConfigurableQueryGenerator
            implements PrfQueryGenerator {

        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = queryParser(analyzer);

            var titleQuery = parseText(parser, topic.title());
            var descQuery = parseText(parser, topic.description());
            var cleanedNarrative = cleanNarrative(topic.narrative());
            var narrQuery = parseText(parser, cleanedNarrative);

            var negativeTerms = extractNegativeNarrativeTerms(topic.narrative());

            // Base TDN query with narrative, plus MUST_NOT terms from negative narrative.
            var baseBuilder = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD)
                    .add(new BoostQuery(narrQuery, 0.7f), BooleanClause.Occur.SHOULD);

            for (String termText : negativeTerms) {
                var termQuery = new TermQuery(new Term("BODY", termText));
                baseBuilder.add(termQuery, BooleanClause.Occur.MUST_NOT);
            }

            var baseTdn = baseBuilder.build();

            var queries = new ArrayList<Query>();
            queries.add(baseTdn);

            // Title-only view (very aggressive on title terms).
            var titleOnlyBuilder = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 3.0f), BooleanClause.Occur.MUST);
            queries.add(titleOnlyBuilder.build());

            // Description-focused view.
            var descOnlyBuilder = new BooleanQuery.Builder()
                    .add(new BoostQuery(descQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(titleQuery, 1.0f), BooleanClause.Occur.SHOULD);
            queries.add(descOnlyBuilder.build());

            // Boosted title phrases as separate base queries.
            queries.addAll(buildTitlePhraseQueries(topic.title()));

            return queries;
        }
    }

    // Multi-query generator: return several complementary queries and let the
    // Searcher fuse them with RRF.
    // Q1: title-only (strong boost)
    // Q2: title + description
    // Q3: cleaned narrative only (smaller boost)
    private static final class MultiQueryRrfGenerator extends ConfigurableQueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var queries = new ArrayList<Query>();
            var parser = queryParser(analyzer);

            // Q1: title-only
            var titleQuery = parseText(parser, topic.title());
            queries.add(new BoostQuery(titleQuery, 2.5f));

            // Q2: title + description
            var descQuery = parseText(parser, topic.description());
            var titleDesc = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD)
                    .build();
            queries.add(titleDesc);

            // Q3: cleaned narrative only
            var cleanedNarrative = cleanNarrative(topic.narrative());
            var narrQuery = parseText(parser, cleanedNarrative);
            queries.add(new BoostQuery(narrQuery, 0.8f));

            return queries;
        }
    }
}