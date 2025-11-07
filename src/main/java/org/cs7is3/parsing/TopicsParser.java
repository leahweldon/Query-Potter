package org.cs7is3.parsing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class TopicsParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("<num>\\s*Number:\\s*(\\d+)");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title>\\s*(.*?)\\s*(?=<desc>|<narr>|</top>|$)", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("<desc>\\s*Description:\\s*(.*?)\\s*(?=<narr>|</top>|$)", Pattern.DOTALL);
    private static final Pattern NARRATIVE_PATTERN =
            Pattern.compile("<narr>\\s*Narrative:\\s*(.*?)\\s*(?=</top>|$)", Pattern.DOTALL);

    private TopicsParser() {
    }

    public static List<Topic> parse(final Path path) throws IOException {
        var topics = new LinkedList<Topic>();
        try (var lines = Files.lines(path)) {
            var current = new StringBuilder();

            lines.forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                if (line.trim().startsWith("<top>")) {
                    // If the current buffer is non-empty, but we haven't found an ending </top> tag, then the file is
                    // improperly formatted.
                    if (!current.isEmpty()) {
                        System.err.printf("[WARN] Found non empty element missing closing tag:\n...\n%s\n...\n", current);
                    }
                    current.setLength(0);
                    current.append(line).append('\n');
                } else if (line.trim().startsWith("</top>")) {
                    current.append(line).append('\n');
                    var rawTopic = current.toString();
                    var topic = new Topic(
                            extract(NUMBER_PATTERN, rawTopic, Integer::parseInt),
                            extract(TITLE_PATTERN, rawTopic, s -> s.replaceAll("\\s+", " ").trim()),
                            extract(DESCRIPTION_PATTERN, rawTopic, s -> s.replaceAll("\\s+", " ").trim()),
                            extract(NARRATIVE_PATTERN, rawTopic, s -> s.replaceAll("\\s+", " ").trim())
                    );
                    topics.add(topic);
                    current.setLength(0);
                } else {
                    current.append(line).append('\n');
                }
            });
        }

        return topics;
    }

    private static <T> T extract(Pattern pattern, String input, Function<String, T> converter) {
        var matcher = pattern.matcher(input);
        if (matcher.find()) {
            return converter.apply(matcher.group(1));
        }
        // This is unexpected but needed to satisfy the compiler since we don't have a default value for the generic
        // type and want to fail hard if we cannot extract something.
        throw new IllegalArgumentException("Pattern not found: " + pattern);
    }


    public record Topic(int number, String title, String description, String narrative) {
    }
}
