package org.cs7is3.parsing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Stream;

public class DocumentsParser {
    private DocumentsParser() {
    }

    // Parse the documents located at the given path. This function assumes the existence of the fbis, fr94, ft and
    // latimes subdirectories within the provided directory, and parses all files into the same document structure,
    // returning a lazily evaluated stream.
    public static Stream<Document> parse(final Path documentsRoot) throws IOException {
        var financialTimes = parse(documentsRoot.resolve("ft"), new FinancialTimesParser());
        var laTimes = parse(documentsRoot.resolve("latimes"), new LATimesParser());
        var foreignBroadcaster = parse(documentsRoot.resolve("fbis"), new ForeignBroadcastParser());
        return Stream.concat(Stream.concat(financialTimes, laTimes), foreignBroadcaster);
    }

    // Recursively, lazily parses a directory. Each file in the directory must be parsed with the same parser.
    private static Stream<Document> parse(final Path root, DocumentParser parser) throws IOException {
        var files = Files.walk(root).filter(path -> {
            var fileName = path.getFileName().toString();
            return fileName.charAt(0) != '.' && !parser.ignoredFiles().contains(fileName) && !Files.isDirectory(path);
        });
        return files.flatMap(path -> {
            var documents = new ArrayList<Document>();
            try (var lines = Files.lines(path, parser.charset())) {
                var current = new StringBuilder();

                lines.forEach(line -> {
                    if (line.isBlank()) {
                        return;
                    }
                    if (line.trim().startsWith("<DOC>")) {
                        // If the current buffer is non-empty, but we haven't found an ending </DOC> tag, then the file is
                        // improperly formatted.
                        if (!current.isEmpty()) {
                            System.err.printf("[WARN] Found non empty element missing closing tag:\n...\n%s\n...\n", current);
                        }
                        current.setLength(0);
                        current.append(line);
                    } else if (line.trim().startsWith("</DOC>")) {
                        current.append(line);
                        var document = parser.parse(current.toString());
                        current.setLength(0);
                        if (document.isValid()) {
                            documents.add(document);
                        }
                    } else {
                        current.append(line);
                    }
                });
            } catch (IOException e) {
                // We can't throw checked exceptions from within a Stream::flatMap function, so we have to wrap this
                // with a UncheckedIOException. In general this is unexpected anyway so it's probably not a bad idea to
                // fail hard.
                throw new UncheckedIOException(e);
            }

            return documents.stream();
        }).onClose(files::close);
    }

    public record Document(String documentNumber, String title, String author, String body, String publicationDate) {
        private static final int TRUNCATED_BODY_LENGTH = 100;

        boolean isValid() {
            return !documentNumber.isBlank();
        }

        // Use toTruncatedString to log a document's properties without polluting stdout with a massive dump of the raw
        // body.
        public String toTruncatedString() {
            return String.format(
                    "Document{documentNumber=%s,title='%s',author='%s',publicationDate=%s,body='%s'}",
                    documentNumber, title, author, publicationDate, truncatedBody()
            );
        }

        private String truncatedBody() {
            if (body.length() > TRUNCATED_BODY_LENGTH) {
                return body.substring(0, TRUNCATED_BODY_LENGTH) + "...";
            }
            return body;
        }
    }

    interface DocumentParser {
        Document parse(final String rawContent);

        Set<String> ignoredFiles();

        default Charset charset() {
            return StandardCharsets.UTF_8;
        }
    }
}
