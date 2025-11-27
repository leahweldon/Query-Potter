package org.cs7is3.parsing;

import com.google.auto.value.AutoValue;
import org.cs7is3.AnalyzerFactory;
import org.cs7is3.QueryGeneratorFactory;
import org.cs7is3.SimilarityFactory;
import org.cs7is3.config.Config;
import org.cs7is3.config.Defaults;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArgsParser {
    private static final ArgSpec<Path> INDEX_ARG = ArgSpec.required("--index", Paths::get);
    private static final ArgSpec<Path> DOCS_ARG = ArgSpec.required("--docs", Paths::get);
    private static final ArgSpec<Path> TOPICS_ARG = ArgSpec.required("--topics", Paths::get);
    private static final ArgSpec<Integer> NUM_DOCS_ARG = ArgSpec.optional("--numDocs", Integer::parseUnsignedInt,
            Defaults.NUMBER_OF_DOCUMENTS);
    private static final ArgSpec<AnalyzerFactory.AnalyzerMode> ANALYZER_ARG = ArgSpec.optional("--analyzer",
            AnalyzerFactory.AnalyzerMode::valueOf, Defaults.ANALYZER_MODE);
    private static final ArgSpec<SimilarityFactory.SimilarityMode> SIMILARITY_ARG = ArgSpec.optional("--similarity",
            SimilarityFactory.SimilarityMode::valueOf, Defaults.SIMILARITY_MODE);
    // Optional BM25 parameters so we can tweak k1 and b from the command line.
    private static final ArgSpec<Float> BM25_K1_ARG = ArgSpec.optional("--bm25k1", Float::parseFloat, Defaults.BM25_K1);
    private static final ArgSpec<Float> BM25_B_ARG = ArgSpec.optional("--bm25b", Float::parseFloat, Defaults.BM25_B);
    private static final List<ArgSpec<?>> INDEX_ARGS = List.of(DOCS_ARG, INDEX_ARG);
    private static final List<ArgSpec<?>> SEARCH_ARGS = List.of(
            INDEX_ARG,
            TOPICS_ARG,
            ArgSpec.required("--output", Paths::get),
            NUM_DOCS_ARG,
            ArgSpec.optional("--query", QueryGeneratorFactory.QueryMode::valueOf, Defaults.QUERY_MODE),
            ArgSpec.optional("--query-parser", QueryGeneratorFactory.QueryParserMode::valueOf, Defaults.QUERY_PARSER));
    private static final List<ArgSpec<?>> EVALUATE_ARGS = List.of(DOCS_ARG, TOPICS_ARG,
            ArgSpec.required("--qrels", Paths::get), NUM_DOCS_ARG, ArgSpec.optional("--index", Boolean::parseBoolean, true));

    private ArgsParser() {
    }

    public static Args parse(String[] rawArgs) throws ArgsParsingException {
        if (rawArgs.length == 0) {
            throw new ArgsParsingException("Failed to parse arguments");
        }

        var mode = Mode.fromRaw(rawArgs[0]);
        var builder = Args.newBuilder();
        if (mode.equals(Mode.HELP)
                || Arrays.stream(rawArgs).anyMatch(arg -> arg.equals("-h") || arg.equals("--help"))) {
            System.out.println(ArgsParsingException.USAGE);
            System.exit(0);
            // We have to have this return statement to satisfy the compiler. In reality the
            // program will terminate so
            // this return value is useless.
            return builder.build();
        }
        if (mode.equals(Mode.EVALUATE)) {
            var args = parse(rawArgs, EVALUATE_ARGS);
            var evaluate = new Args.Evaluate(
                    args.get("--docs", Path.class),
                    args.get("--topics", Path.class),
                    args.get("--qrels", Path.class),
                    args.get("--numDocs", Integer.class),
                    args.get("--index", Boolean.class));
            return builder.setEvaluate(evaluate).build();
        }
        if (mode.equals(Mode.INDEX)) {
            var args = parse(rawArgs, INDEX_ARGS);
            var index = new Args.Index(args.get("--docs", Path.class), args.get("--index", Path.class));
            return builder
                    .setIndex(index)
                    .setAnalyzerMode(args)
                    .setSimilarityMode(args)
                    .setBm25Params(args)
                    .build();
        }

        var args = parse(rawArgs, SEARCH_ARGS);
        var search = new Args.Search(
                /* index= */ args.get("--index", Path.class),
                /* topics= */ args.get("--topics", Path.class),
                /* output= */ args.get("--output", Path.class),
                /* numDocs= */ args.get("--numDocs", Integer.class),
                /* queryMode = */ args.get("--query", QueryGeneratorFactory.QueryMode.class),
                /* queryParserMode = */ args.get("--query-parser", QueryGeneratorFactory.QueryParserMode.class));
        return builder
                .setSearch(search)
                .setAnalyzerMode(args)
                .setSimilarityMode(args)
                .setBm25Params(args)
                .build();
    }

    private static ParsedArgs parse(String[] rawArgs, List<ArgSpec<?>> baseSpecs) throws ArgsParsingException {
        var parsed = new HashMap<String, Object>(baseSpecs.size());
        var specs = Stream.concat(
                baseSpecs.stream(),
                Stream.of(ANALYZER_ARG, SIMILARITY_ARG, BM25_K1_ARG, BM25_B_ARG))
                .collect(Collectors.toUnmodifiableSet());

        var namedSpecs = specs.stream().collect(Collectors.toMap(ArgSpec::name, Function.identity()));

        for (var i = 1; i < rawArgs.length; i++) {
            var arg = rawArgs[i];
            var spec = namedSpecs.get(arg);

            if (spec == null) {
                // Arg is unrecognised so we skip it
                continue;
            }
            if (parsed.containsKey(arg)) {
                throw new ArgsParsingException(String.format("Duplicate arg: '%s'", arg));
            }
            if (i == rawArgs.length - 1) {
                throw new ArgsParsingException(String.format("Expecting value for '%s'", arg));
            }

            parsed.put(arg, spec.parse(rawArgs[++i]));
        }

        for (var spec : specs) {
            if (!parsed.containsKey(spec.name())) {
                if (!spec.optional()) {
                    throw new ArgsParsingException(String.format("Expected '%s' to be present", spec.name()));
                }
                parsed.put(spec.name(), spec.defaultValue());
            }
        }

        return new ParsedArgs(parsed);
    }

    public static final class ArgsParsingException extends Exception {
        private static final String USAGE = """
                Usage: <main class> <command> [options]
                Commands:
                    index    Build an index from a set of documents
                    search   Run a search using the provided topics
                    help     Print this message and exit
                    evaluate Evaluates all permutations of queries, analyzers and similarity scoring methods

                Options for `index`:
                    --docs <path>               Path to the directory containing the documents
                    --index <path>              Path to where the index will be stored
                    --analyzer <analyzer>       The analyzer to use. Should match the index analyzer (default: ENGLISH)
                    --similarity <similarity>   The similarity scorer to use. Should match the index similarity (default: BM25)
                    --bm25k1 <float>            k1 parameter for BM25 (default: 1.2)
                    --bm25b <float>             b parameter for BM25 (default: 0.75)

                Example:
                    java -jar target/cs7is3-search-1.0.0.jar index --docs "Assignment Two" --index index

                Options for `search`:
                    --index <path>              Path to the existing index for searching
                    --topics <path>             Path to the topics file to generate queries from
                    --output <path>             Path to where the search results should be saved
                    --numDocs <int>             Maximum number of documents to retrieve per topic (default: 1000)
                    --analyzer <analyzer>       The analyzer to use. Should match the index analyzer (default: ENGLISH)
                    --similarity <similarity>   The similarity scorer to use. Should match the index similarity (default: BM25)
                    --query <query>             The query generator to use (default: BOOSTED_TITLE)
                    --query-parser <parser>     The query parser to use (default: UNIFORM_MULTI_FIELDS)
                    --bm25k1 <float>            k1 parameter for BM25 (default: 1.2)
                    --bm25b <float>             b parameter for BM25 (default: 0.75)

                Example:
                    java -jar target/cs7is3-search-1.0.0.jar search --index index --topics topics --output runs/student.run --numDocs 1000

                Options for `evaluate`:
                    --docs <path>       Path to the directory containing the documents
                    --topics <path>     Path to the topics file to generate queries from
                    --qrels <path>      Path to the relevance judgements file to use in evaluation
                    --numDocs <int>     Maximum number of documents to retrieve per topic (default: 1000)

                Example:
                    java -jar target/cs7is3-search-1.0.0.jar evaluate --docs "Assignment Two" --topics topics --qrels qrels --numDocs 1000

                General options:
                    -h, --help  Display this message and exit
                """;

        private ArgsParsingException(String message) {
            super(String.format("%s\n%s", message, USAGE));
        }
    }

    @AutoValue
    public static abstract class Args {
        public record Index(Path docs, Path index) {
        }

        public record Search(Path index, Path topics, Path output, int numDocs,
                QueryGeneratorFactory.QueryMode queryMode, QueryGeneratorFactory.QueryParserMode queryParserMode) {
        }

        public record Evaluate(Path docs, Path topics, Path qrelsFile, int numDocs, boolean index) {
        }

        public abstract Optional<Index> index();

        public abstract Optional<Search> search();

        public abstract Optional<Evaluate> evaluate();

        public abstract Optional<AnalyzerFactory.AnalyzerMode> analyzerMode();

        public abstract Optional<SimilarityFactory.SimilarityMode> similarityMode();

        public boolean isEvaluateMode() {
            return index().isEmpty() && search().isEmpty() && evaluate().isPresent();
        }

        public abstract Config config();

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder setIndex(Index index);

            abstract Builder setSearch(Search search);

            abstract Builder setEvaluate(Evaluate evaluate);

            Builder setAnalyzerMode(ParsedArgs args) throws ArgsParsingException {
                return setAnalyzerMode(args.get("--analyzer", AnalyzerFactory.AnalyzerMode.class));
            }

            abstract Builder setAnalyzerMode(AnalyzerFactory.AnalyzerMode mode);

            Builder setSimilarityMode(ParsedArgs args) throws ArgsParsingException {
                return setSimilarityMode(args.get("--similarity", SimilarityFactory.SimilarityMode.class));
            }

            abstract Builder setSimilarityMode(SimilarityFactory.SimilarityMode mode);

            abstract Config config();

            abstract Builder setConfig(Config config);

            Builder setBm25Params(ParsedArgs args) throws ArgsParsingException {
                var k1 = args.get("--bm25k1", Float.class);
                var b = args.get("--bm25b", Float.class);
                return setConfig(config().toBuilder().bm25(Config.Bm25.create(k1, b)).build());
            }

            Args build() throws ArgsParsingException {
                var result = autoBuild();
                if (result.isEvaluateMode()) {
                    return result;
                }

                var missing = new StringBuilder();
                if (result.analyzerMode().isEmpty()) {
                    missing.append(" --analyzer");
                }
                if (result.similarityMode().isEmpty()) {
                    missing.append(" --similarity");
                }
                if (missing.isEmpty()) {
                    return result;
                }

                throw new ArgsParsingException("Missing parameters: " + missing);
            }

            protected abstract Args autoBuild();
        }

        static Builder newBuilder() {
            return new AutoValue_ArgsParser_Args.Builder().setConfig(Config.newBuilder().build());
        }
    }

    private enum Mode {
        INDEX, SEARCH, HELP, EVALUATE;

        static Mode fromRaw(String raw) throws ArgsParsingException {
            try {
                return Mode.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ArgsParsingException(String.format("Unknown command '%s'", raw));
            }
        }
    }

    private record ArgSpec<T>(String name, Function<String, T> parser, T defaultValue) {
        static <T> ArgSpec<T> optional(String name, Function<String, T> parser, T defaultValue) {
            return new ArgSpec<>(name, parser, defaultValue);
        }

        static <T> ArgSpec<T> required(String name, Function<String, T> parser) {
            return new ArgSpec<>(name, parser, null);
        }

        boolean optional() {
            return defaultValue != null;
        }

        T parse(String raw) throws ArgsParsingException {
            try {
                return parser.apply(raw);
            } catch (Exception swallowed) {
                throw new ArgsParsingException(String.format("Could not parse value for '%s': '%s'", name, raw));
            }
        }
    }

    private record ParsedArgs(Map<String, ?> args) {
        <T> T get(String name, Class<T> clazz) throws ArgsParsingException {
            var value = args.get(name);
            if (!clazz.isInstance(value)) {
                throw new ArgsParsingException(String.format("Invalid format for '%s': '%s'", name, value));
            }
            return clazz.cast(value);
        }
    }
}