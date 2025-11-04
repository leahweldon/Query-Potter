package org.cs7is3;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class ArgsParser {
    private static final Set<String> INDEX_ARGS = Set.of("--docs", "--index");
    private static final Set<String> SEARCH_ARGS = Set.of("--index", "--topics", "--output", "--numDocs");

    private ArgsParser() {
    }

    static Args parse(String[] rawArgs) throws ArgsParsingException {
        if (rawArgs.length == 0) {
            throw new ArgsParsingException("Failed to parse arguments");
        }

        var mode = Args.Mode.fromRaw(rawArgs[0]);
        if (mode.equals(Args.Mode.HELP) || Arrays.stream(rawArgs).anyMatch(arg -> arg.equals("-h") || arg.equals("--help"))) {
            System.out.println(ArgsParsingException.USAGE);
            System.exit(0);
            // We have to have this return statement to satisfy the compiler. In reality the program will terminate so
            // this return value is useless.
            return new Args(Args.Mode.HELP, null, null);
        }
        if (mode.equals(Args.Mode.INDEX)) {
            var namedArgs = parse(rawArgs, INDEX_ARGS, Set.of());
            var index = new Args.Index(
                    /* docs= */ parsePath(namedArgs.get("--docs")),
                    /* index= */ parsePath(namedArgs.get("--index"))
            );
            return new Args(mode, index, null);
        }

        var namedArgs = parse(rawArgs, SEARCH_ARGS, Set.of("--numDocs"));
        var search = new Args.Search(
                /* index= */ parsePath(namedArgs.get("--index")),
                /* topics= */ parsePath(namedArgs.get("--topics")),
                /* output= */ parsePath(namedArgs.get("--output")),
                /* numDocs= */ parseInt(namedArgs.getOrDefault("--numDocs", "1000"))
        );
        return new Args(Args.Mode.SEARCH, null, search);
    }

    private static Map<String, String> parse(String[] rawArgs, Set<String> argNames, Set<String> optionalArgs) throws ArgsParsingException {
        var args = new HashMap<String, String>(argNames.size());

        for (var i = 1; i < rawArgs.length; i++) {
            var arg = rawArgs[i];
            if (argNames.contains(arg)) {
                if (args.containsKey(arg)) {
                    throw new ArgsParsingException(String.format("Duplicate arg: '%s'", arg));
                }
                if (i == rawArgs.length - 1) {
                    throw new ArgsParsingException(String.format("Expecting value for '%s'", arg));
                }

                args.put(arg, rawArgs[++i]);
            }
        }

        if (args.size() != argNames.size() && !optionalArgs.isEmpty()) {
            for (var arg : argNames) {
                if (!args.containsKey(arg) && !optionalArgs.contains(arg)) {
                    throw new ArgsParsingException(String.format("Expected '%s' to be present", arg));
                }
            }
        }

        return args;
    }

    private static Path parsePath(String path) throws ArgsParsingException {
        try {
            return Paths.get(path);
        } catch (InvalidPathException ignored) {
            throw new ArgsParsingException(String.format("Path '%s' could not be parsed", path));
        }
    }

    private static int parseInt(String value) throws ArgsParsingException {
        try {
            return Integer.parseUnsignedInt(value);
        } catch (NumberFormatException ignored) {
            throw new ArgsParsingException(String.format("Number '%s' could not be parsed as an unsigned integer", value));
        }
    }

    public static final class ArgsParsingException extends Exception {
        private static final String USAGE = """
                Usage: <main class> <command> [options]
                Commands:
                    index    Build an index from a set of documents
                    search   Run a search using the provided topics
                    help     Print this message and exit
                
                Options for `index`:
                    --docs <path>   Path to the directory containing the documents
                    --index <path>  Path to where the index will be stored
                
                Example:
                    java -jar target/cs7is3-search-1.0.0.jar index --docs "Assignment Two" --index index
                
                Options for `search`:
                    --index <path>  Path to the existing index for searching
                    --topics <path> Path to the topics file to generate queries from
                    --output <path> Path to where the search results should be saved
                    --numDocs <int> Maximum number of documents to retrieve per topic (default: 1000)
                
                Example:
                    java -jar target/cs7is3-search-1.0.0.jar search --index index --topics topics --output runs/student.run --numDocs 1000
                
                General options:
                    -h, --help  Display this message and exit
                """;

        private ArgsParsingException(String message) {
            super(String.format("%s\n%s", message, USAGE));
        }
    }

    record Args(Mode mode, Index index, Search search) {
        enum Mode {
            INDEX, SEARCH, HELP;

            static Mode fromRaw(String raw) throws ArgsParsingException {
                try {
                    return Mode.valueOf(raw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new ArgsParsingException(String.format("Unknown command '%s'", raw));
                }
            }
        }

        record Index(Path docs, Path index) {
        }

        record Search(Path index, Path topics, Path output, int numDocs) {
        }

        boolean hasIndex() {
            return mode.equals(Mode.INDEX) && index != null;
        }

        boolean hasSearch() {
            return mode.equals(Mode.SEARCH) && search != null;
        }
    }
}
