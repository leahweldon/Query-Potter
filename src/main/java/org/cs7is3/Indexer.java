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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.cs7is3.parsing.DocumentsParser;

import java.io.IOException;
import java.nio.file.Path;

public class Indexer {
    private final Analyzer analyzer;

    Indexer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void buildIndex(final Path docsPath, final Path indexPath) throws IOException {
        System.out.println("Starting indexing.");
        System.out.println("Documents path: " + docsPath.toAbsolutePath());
        System.out.println("Index path: " + indexPath.toAbsolutePath());
        
        Directory directory = FSDirectory.open(indexPath);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        try (IndexWriter writer = new IndexWriter(directory, config);
             var documents = DocumentsParser.parse(docsPath)) {

            documents.forEach(doc -> {
                Document luceneDoc = new Document();

                luceneDoc.add(new StringField("DOCNO", doc.documentNumber(), Field.Store.YES));
                luceneDoc.add(new TextField("TITLE", doc.title() != null ? doc.title() : "", Field.Store.YES));
                luceneDoc.add(new StringField("AUTHOR", doc.author() != null ? doc.author() : "", Field.Store.YES));
                luceneDoc.add(new TextField("BODY", doc.body() != null ? doc.body() : "", Field.Store.YES));
                luceneDoc.add(new StringField("DATE", doc.publicationDate() != null ? doc.publicationDate() : "", Field.Store.YES));

                try {
                    writer.addDocument(luceneDoc);
                } catch (IOException e) {
                    System.err.printf("Failed to index document %s: %s\n", doc.documentNumber(), e.getMessage());
                }
            });

            writer.commit();
            System.out.println("Indexing completed successfully.");
        }
    }
}
