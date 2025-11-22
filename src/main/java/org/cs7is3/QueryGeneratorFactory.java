package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.cs7is3.parsing.TopicsParser;

import java.util.ArrayList;
import java.util.List;

public class QueryGeneratorFactory {
    // A QueryGeneratorFactory constructs queries for a given topic. These queries are then run against the index and
    // combined using Reciprocal Rank Fusion into 1 single rank that is then used as the output for a single topic.
    // Multiple queries may be returned or just a singleton.
    interface QueryGenerator {
        List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException;
    }

    public enum QueryMode {
        BOOSTED_TITLE,                    // existing baseline
        TITLE_ONLY,                       // title only
        TITLE_DESC,                       // title + description
        TITLE_DESC_NARRATIVE_CLEANED,     // title + desc + cleaned narrative
        MULTI_QUERY_RRF                   // multiple complementary queries fused via RRF
    }

    static QueryGenerator getInstance(QueryMode mode) {
        return switch (mode) {
            case BOOSTED_TITLE -> new BoostedTitleQueryGenerator();
            case TITLE_ONLY -> new TitleOnlyQueryGenerator();
            case TITLE_DESC -> new TitleDescQueryGenerator();
            case TITLE_DESC_NARRATIVE_CLEANED -> new TitleDescNarrativeCleanedQueryGenerator();
            case MULTI_QUERY_RRF -> new MultiQueryRrfGenerator();
        };
    }

    // We mainly care about title and body text for content matching. Author/date can carry weaker signal.
    private static final String[] CORE_FIELDS = {"TITLE", "BODY"};
    private static final String[] ALL_FIELDS = {"TITLE", "AUTHOR", "BODY", "DATE"};

     // Basic normalisation for topic text: collapse whitespace and trim.
    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    // Cleaning up the narrative by dropping sentences that explicitly talk about irrelevance.
    // This is simple, but it helps avoid putting a lot of "not relevant" boilerplate into the query.
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

    // Helper to parse some text using a MultiFieldQueryParser after normalisation and escaping.
    private static Query parseText(MultiFieldQueryParser parser, String text) throws ParseException {
        var normalised = normalise(text);
        if (normalised.isEmpty()) {
            // Returns an empty BooleanQuery if there is nothing to parse.
            return new BooleanQuery.Builder().build();
        }
        return parser.parse(QueryParserBase.escape(normalised));
    }

    private static final class BoostedTitleQueryGenerator implements QueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = new MultiFieldQueryParser(CORE_FIELDS, analyzer);
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
    private static final class TitleOnlyQueryGenerator implements QueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = new MultiFieldQueryParser(CORE_FIELDS, analyzer);
            var titleQuery = parseText(parser, topic.title());
            var boosted = new BoostQuery(titleQuery, 2.0f);
            return List.of(boosted);
        }
    }

    // Title + description
    private static final class TitleDescQueryGenerator implements QueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = new MultiFieldQueryParser(CORE_FIELDS, analyzer);
            var titleQuery = parseText(parser, topic.title());
            var descQuery = parseText(parser, topic.description());

            var builder = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descQuery, 1.0f), BooleanClause.Occur.SHOULD);

            return List.of(builder.build());
        }
    }

    // Title + description + cleaned narrative
    private static final class TitleDescNarrativeCleanedQueryGenerator implements QueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = new MultiFieldQueryParser(CORE_FIELDS, analyzer);
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

    // Multi-query generator: return several complementary queries and let the Searcher fuse them with RRF.
    // Q1: title-only (strong boost)
    // Q2: title + description
    // Q3: cleaned narrative only (smaller boost)
    private static final class MultiQueryRrfGenerator implements QueryGenerator {
        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var queries = new ArrayList<Query>();
            var parser = new MultiFieldQueryParser(CORE_FIELDS, analyzer);

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
