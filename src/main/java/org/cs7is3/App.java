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
    public static void main(String[] args) throws ArgsParser.ArgsParsingException, IOException {
        var parsedArgs = ArgsParser.parse(args);

        var analyzer = AnalyzerFactory.getInstance(parsedArgs.analyzerMode());
        var similarity = SimilarityFactory.getInstance(
                parsedArgs.similarityMode(),
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
            var queryGenerator = QueryGeneratorFactory.getInstance(parsedArgs.search().get().queryMode());
            var searcher = new Searcher(analyzer, similarity, queryGenerator);
            searcher.searchTopics(
                parsedArgs.search().get().index(),
                parsedArgs.search().get().topics(),
                parsedArgs.search().get().output(),
                parsedArgs.search().get().numDocs()
            );
        }
    }
}
