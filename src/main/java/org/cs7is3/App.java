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

import java.io.IOException;

public class App {
    public static void main(String[] args) throws ArgsParser.ArgsParsingException, IOException {
        var parsedArgs = ArgsParser.parse(args);
        if (parsedArgs.hasIndexArgs()) {
            var indexer = new Indexer();
            indexer.buildIndex(parsedArgs.indexArgs().docs(), parsedArgs.indexArgs().index());
        } else if (parsedArgs.hasSearchArgs()) {
            var searcher = new Searcher();
            searcher.searchTopics(
                    parsedArgs.searchArgs().index(),
                    parsedArgs.searchArgs().topics(),
                    parsedArgs.searchArgs().output(),
                    parsedArgs.searchArgs().numDocs()
            );
        }
    }
}
