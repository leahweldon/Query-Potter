package org.cs7is3;

// This class should handle command-line arguments and coordinate between Indexer and Searcher
// 
// Required command-line interface:
//   java -jar your-jar.jar index --docs "Assignment Two" --index index
//   java -jar your-jar.jar search --index index --topics topics --output runs/student.run --numDocs 1000
//
// The GitHub Actions workflow expects:
// 1. Maven build to succeed (mvn clean package)
// 2. Index command to create an index from the dataset
// 3. Search command to produce a TREC-format run file with exactly 1000 results per topic
// 4. Output format: "topic_id Q0 docno rank score run_tag"


import org.cs7is3.parsing.ArgsParser;

import java.io.IOException;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws ArgsParser.ArgsParsingException, IOException, InterruptedException {
        var parsedArgs = ArgsParser.parse(args);

        if (parsedArgs.isEvaluateMode()) {
            Evaluator.evaluate(parsedArgs.evaluate().get());
            return;
        }

        var analyzer = AnalyzerFactory.getInstance(parsedArgs.analyzerMode().get());
        var similarity = SimilarityFactory.getInstance(
                parsedArgs.similarityMode().get(),
                parsedArgs.bm25k1(),
                parsedArgs.bm25b()
        );
        if (parsedArgs.index().isPresent()) {
            var docsPath = parsedArgs.index().get().docs();
            var indexPath = parsedArgs.index().get().index();

            // Add defaults only if null, preserving existing logic
            if (docsPath == null) {
                docsPath = Paths.get("Assignment Two");
            }
            if (indexPath == null) {
                indexPath = Paths.get("lucene-index");
            }

            var indexer = new Indexer(analyzer, similarity);
            indexer.buildIndex(docsPath, indexPath);
        } else if (parsedArgs.search().isPresent()) {
            var searchArgs = parsedArgs.search().get();
            var queryGenerator = QueryGeneratorFactory.getInstance(searchArgs.queryMode());
            // Added a Run Tag
            // Example: bm25_k1_1.20_b_0.75_MULTI_QUERY_RRF
            var runTag = String.format(
                    "bm25_k1_%.2f_b_%.2f_%s",
                    parsedArgs.bm25k1(),
                    parsedArgs.bm25b(),
                    searchArgs.queryMode().name()
            );

            var searcher = new Searcher(analyzer, similarity, queryGenerator, runTag);
            searcher.searchTopics(
                    searchArgs.index(),
                    searchArgs.topics(),
                    searchArgs.output(),
                    searchArgs.numDocs()
            );
        }
    }
}
