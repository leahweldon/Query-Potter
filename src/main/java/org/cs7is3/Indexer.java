package org.cs7is3;

// TODO: Implement your Lucene indexer
// This class should build a Lucene index from the document collection
//
// Requirements:
// 1. Parse documents from the "Assignment Two" dataset
// 2. Extract relevant fields (DOCNO, TITLE, TEXT, etc.)
// 3. Create a Lucene index with appropriate analyzers
// 4. Handle document parsing errors gracefully
//
// The GitHub Actions workflow will call:
//   indexer.buildIndex(Path docsPath, Path indexPath)

import org.cs7is3.parsing.DocumentsParser;

import java.nio.file.Path;

public class Indexer {
    public void buildIndex(final Path docsPath, final Path indexPath) throws java.io.IOException {
        try (var documents = DocumentsParser.parse(docsPath)) {
            documents.map(DocumentsParser.Document::toTruncatedString).forEach(System.out::println);
        }
    }
}
