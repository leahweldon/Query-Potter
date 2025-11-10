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
import org.apache.lucene.search.similarities.Similarity;
import org.cs7is3.parsing.TopicsParser;

import java.nio.file.Path;

public class Searcher {
    private final Analyzer analyzer;
    private final Similarity similarity;

    Searcher(Analyzer analyzer, Similarity similarity) {
        this.analyzer = analyzer;
        this.similarity = similarity;
    }

    public void searchTopics(Path indexPath, Path topicsPath, Path outputRun, int numDocs) throws java.io.IOException {
        var topics = TopicsParser.parse(topicsPath);
        topics.forEach(System.out::println);
        // TODO: Implement your search logic
        System.out.println("TODO: Implement your Lucene searcher");
    }
}
