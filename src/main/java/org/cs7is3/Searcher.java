package org.cs7is3;

// TODO: Implement your Lucene searcher
// This class should search topics against the index and produce TREC-format results
//
// Requirements:
// 1. Parse topics from the topics file
// 2. Generate queries from topic information (title, description, narrative)
// 3. Execute searches against the Lucene index
// 4. Write TREC-format results: "topic_id Q0 docno rank score run_tag"
// 5. Output exactly 1000 results per topic
//
// The GitHub Actions workflow will call:
//   searcher.searchTopics(Path indexPath, Path topicsPath, Path outputRun, int numDocs)

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.cs7is3.parsing.TopicsParser;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Searcher {
    // Empirical value used for RRF, originally set to 60.0 per Cormack et. al (2009, DOI:
    // 10.1145/1571941.1572114). Empirically we found that using a value of 100 was more successful, likely due to the
    // larger number of input queries per topic, meaning we want a smoother drop-off.
    private static final Double K = 100.0;
    // PRF configuration: these can be tuned later.
    private static final int PRF_FEEDBACK_DOCS = 50; // how many top docs to use as feedback
    private static final int PRF_EXPANSION_DOCS = 20; // how many of those docs to read terms from
    private static final int PRF_EXPANSION_TERMS = 30; // how many expansion terms to add
    // We have large boosts coming from some of the queries, and longer documents dilute the individual contributions
    // from PRF terms. Running a high boost here means that the impact of using PRF is meaningful.
    private static final float PRF_EXPANSION_BOOST = 4.6f;

    private final Analyzer analyzer;
    private final Similarity similarity;
    private final QueryGeneratorFactory.QueryGenerator queryGenerator;
    private final String runTag;

    Searcher(Analyzer analyzer, Similarity similarity, QueryGeneratorFactory.QueryGenerator queryGenerator,
            String runTag) {
        this.analyzer = analyzer;
        this.similarity = similarity;
        this.queryGenerator = queryGenerator;
        this.runTag = runTag;
    }

    // Convenience constructor used by Evaluator (which does not provide a runTag).
    // Falls back to a neutral default run tag.
    Searcher(Analyzer analyzer,
            Similarity similarity,
            QueryGeneratorFactory.QueryGenerator queryGenerator) {
        this(analyzer, similarity, queryGenerator, "eval-default");
    }

    public void searchTopics(Path indexPath, Path topicsPath, Path outputRun, int numDocs) throws IOException {
        var topics = TopicsParser.parse(topicsPath);
        var output = new StringBuilder();

        try (var directory = FSDirectory.open(indexPath); var reader = DirectoryReader.open(directory)) {
            var searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);

            topics.forEach(topic -> {
                try {
                    // Base queries from configured QueryGenerator
                    var baseQueries = queryGenerator.generateQueries(analyzer, topic);
                    List<org.apache.lucene.search.Query> queries = baseQueries;

                    // If PRF-enabled generator, augment with expansion queries built from
                    // top-ranked docs
                    if (queryGenerator instanceof QueryGeneratorFactory.PrfQueryGenerator) {
                        queries = applyPrfExpansion(analyzer, searcher, baseQueries, topic, numDocs);
                    }

                    var results = queries.stream().map(query -> {
                        try {
                            var scoreDocs = searcher.search(query, numDocs).scoreDocs;
                            return Arrays.stream(scoreDocs).sorted(ScoreDocComparator.INSTANCE).toList();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).toList();

                    var rankedResults = reciprocalRankFusion(results, numDocs);
                    var rank = new AtomicInteger(0);
                    rankedResults.forEach(result -> {
                        try {
                            var fields = searcher.storedFields().document(result.doc);
                            var score = result.score;
                            var docNo = fields.get("DOCNO");
                            output.append(String.format("%s Q0 %s %d %.12f %s\n", topic.number(), docNo,
                                    rank.incrementAndGet(), score, runTag));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        try (var writer = Files.newBufferedWriter(
                outputRun,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(output.toString());
        }
    }

    // For PRF: given base queries, build expanded queries using top-ranked
    // documents as pseudo-relevant feedback
    // Returns list containing both original base queries & expansion queries
    private List<Query> applyPrfExpansion(
            Analyzer analyzer,
            IndexSearcher searcher,
            List<Query> baseQueries,
            TopicsParser.Topic topic,
            int numDocs) {

        var allQueries = new ArrayList<Query>(baseQueries);

        for (Query baseQuery : baseQueries) {
            // First pass: get top feedback docs for base query
            org.apache.lucene.search.TopDocs feedbackTopDocs;
            try {
                feedbackTopDocs = searcher.search(baseQuery, PRF_FEEDBACK_DOCS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            var termScores = new HashMap<String, Float>();

            // Collect terms from top PRF_EXPANSION_DOCS documents
            int docsToUse = Math.min(PRF_EXPANSION_DOCS, feedbackTopDocs.scoreDocs.length);
            for (int i = 0; i < docsToUse; i++) {
                ScoreDoc sd = feedbackTopDocs.scoreDocs[i];
                org.apache.lucene.document.Document doc;
                try {
                    doc = searcher.storedFields().document(sd.doc);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                // Focus on BODY & TITLE as feedback fields
                accumulateTermsFromField(analyzer, termScores, "BODY", doc.get("BODY"));
                accumulateTermsFromField(analyzer, termScores, "TITLE", doc.get("TITLE"));
            }

            // TF×IDF scoring
            var reader = searcher.getIndexReader();
            int maxDoc = reader.maxDoc();

            var weightedScores = new HashMap<String, Float>();

            termScores.forEach((termText, tf) -> {
                try {
                    int df = reader.docFreq(new Term("BODY", termText));
                    if (df <= 0) {
                        return;
                    }
                    float idf = (float) Math.log((maxDoc + 1.0) / (df + 1.0));
                    // basic idf floor to remove extremely common junk
                    if (idf < 0.1f) {
                        return;
                    }
                    float score = tf * idf;
                    weightedScores.put(termText, score);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            var topTerms = weightedScores.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .limit(PRF_EXPANSION_TERMS)
                    .map(Map.Entry::getKey)
                    .toList();

            if (!topTerms.isEmpty()) {
                Query expandedQuery = buildExpandedQuery(baseQuery, topTerms);
                allQueries.add(expandedQuery);
            }
        }

        return allQueries;
    }

    // Tokenize given field text & accumulate term frequencies into score map
    private void accumulateTermsFromField(
            Analyzer analyzer,
            Map<String, Float> termScores,
            String fieldName,
            String text) {

        if (text == null || text.isBlank()) {
            return;
        }

        try (TokenStream ts = analyzer.tokenStream(fieldName, text)) {
            var termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                var term = termAttr.toString();
                // Increase score - could be weighted by document score etc. if we want to mak
                // it more sophisticated
                termScores.merge(term, 1.0f, Float::sum);
            }
            ts.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Build expanded query that keeps original base query and adds expansion terms
    // on BODY field with moderate boost
    private org.apache.lucene.search.Query buildExpandedQuery(
            org.apache.lucene.search.Query baseQuery,
            List<String> expansionTerms) {

        var builder = new BooleanQuery.Builder();

        // Keep the original query as a strong MUST
        builder.add(baseQuery, BooleanClause.Occur.MUST);

        // Add expansion terms as BODY terms with smaller boost
        for (var termText : expansionTerms) {
            var termQuery = new TermQuery(new Term("BODY", termText));
            var boosted = new BoostQuery(termQuery, PRF_EXPANSION_BOOST);
            builder.add(boosted, BooleanClause.Occur.SHOULD);
        }

        return builder.build();
    }

    // Performs Reciprocal Rank Fusion (RRF) to combine multiple rankings for a
    // given topic into one. Documents that
    // appear more frequently in separate rankings, or appear higher in specific
    // rankings are rewarded versus fringe
    // documents. This can be tailored using the K constant.
    private static List<ScoreDoc> reciprocalRankFusion(List<List<ScoreDoc>> scoreDocs, int numDocs) {
        var documentsToRanks = new HashMap<Integer, Double>();
        scoreDocs.forEach(docs -> {
            for (var rank = 0; rank < docs.size(); rank++) {
                var document = docs.get(rank);
                var partialScore = 1.0 / (K + (double) (rank + 1));
                documentsToRanks.merge(document.doc, partialScore, Double::sum);
            }
        });
        return documentsToRanks.entrySet().stream()
                .map(entry -> new ScoreDoc(entry.getKey(), entry.getValue().floatValue()))
                .sorted(ScoreDocComparator.INSTANCE).limit(numDocs).toList();
    }

    private static final class ScoreDocComparator implements Comparator<ScoreDoc> {
        private static final Comparator<ScoreDoc> INSTANCE = new ScoreDocComparator();

        @Override
        public int compare(ScoreDoc a, ScoreDoc b) {
            // Copied from ScoreDoc.COMPARATOR in version 10.* of Lucene
            // src:
            // https://github.com/apache/lucene/blob/e02bdb4c3c547488342b423e1b9b2b25519bd427/lucene/core/src/java/org/apache/lucene/search/ScoreDoc.java#L57
            if (a.score > b.score) {
                return -1;
            } else {
                return a.score < b.score ? 1 : a.doc - b.doc;
            }
        }
    }
}