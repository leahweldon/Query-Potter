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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Searcher {
    // Empirical value used for RRF, set to 60.0 per Cormack et. al (2009, DOI: 10.1145/1571941.1572114).
    private static final Double K = 60.0;

    private final Analyzer analyzer;
    private final Similarity similarity;
    private final QueryGeneratorFactory.QueryGenerator queryGenerator;

    Searcher(Analyzer analyzer, Similarity similarity, QueryGeneratorFactory.QueryGenerator queryGenerator) {
        this.analyzer = analyzer;
        this.similarity = similarity;
        this.queryGenerator = queryGenerator;
    }

    public void searchTopics(Path indexPath, Path topicsPath, Path outputRun, int numDocs) throws IOException {
        var topics = TopicsParser.parse(topicsPath);
        var output = new StringBuilder();

        try (var directory = FSDirectory.open(indexPath); var reader = DirectoryReader.open(directory)) {
            var searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);

            topics.forEach(topic -> {
                try {
                    var queries = queryGenerator.generateQueries(analyzer, topic);
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
                            output.append(String.format("%s Q0 %s %d %.12f run-id\n", topic.number(), docNo, rank.incrementAndGet(), score));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        try (var writer =
                     Files.newBufferedWriter(
                             outputRun,
                             StandardCharsets.UTF_8,
                             StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING,
                             StandardOpenOption.WRITE)) {
            writer.write(output.toString());
        }
    }

    // Performs Reciprocal Rank Fusion (RRF) to combine multiple rankings for a given topic into one. Documents that
    // appear more frequently in separate rankings, or appear higher in specific rankings are rewarded versus fringe
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
        return documentsToRanks.entrySet().stream().map(entry ->
                new ScoreDoc(entry.getKey(), entry.getValue().floatValue())
        ).sorted(ScoreDocComparator.INSTANCE).limit(numDocs).toList();
    }

    private static final class ScoreDocComparator implements Comparator<ScoreDoc> {
        private static final Comparator<ScoreDoc> INSTANCE = new ScoreDocComparator();

        @Override
        public int compare(ScoreDoc a, ScoreDoc b) {
            // Copied from ScoreDoc.COMPARATOR in version 10.* of Lucene
            // src: https://github.com/apache/lucene/blob/e02bdb4c3c547488342b423e1b9b2b25519bd427/lucene/core/src/java/org/apache/lucene/search/ScoreDoc.java#L57
            if (a.score > b.score) {
                return -1;
            } else {
                return a.score < b.score ? 1 : a.doc - b.doc;
            }
        }
    }
}
