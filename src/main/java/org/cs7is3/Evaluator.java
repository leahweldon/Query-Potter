package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.Similarity;
import org.cs7is3.config.Config;
import org.cs7is3.parsing.ArgsParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Evaluator {
    private Evaluator() {
    }

    public static void evaluate(ArgsParser.Args.Evaluate args) throws IOException {
        var outputDirectory = Files.createTempDirectory("tmp");
        System.out.printf("Opened temporary directory at [%s]...\n", outputDirectory.toString());
        outputDirectory.toFile().deleteOnExit();
        var futureResults = new ArrayList<CompletableFuture<EvaluationResult>>();

        var indexes = buildIndexes(args);

        var searchPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (var index : indexes) {
            for (var queryMode : QueryGeneratorFactory.QueryMode.values()) {
                for (var queryParserMode : QueryGeneratorFactory.QueryParserMode.values()) {
                    var future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return evaluate(args, outputDirectory, index, queryMode, queryParserMode);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }, searchPool);
                    futureResults.add(future);
                }
            }
        }

        var results = futureResults.stream().map(CompletableFuture::join).collect(Collectors.toList());
        searchPool.shutdown();
        if (results.isEmpty()) {
            return;
        }

        results.sort(EvaluationResult.COMPARATOR);
        System.out.println("-------------------------\nCompleted evaluating all approaches!\n--------------------");
        results.forEach(System.out::println);
    }

    private static Set<Index> buildIndexes(ArgsParser.Args.Evaluate args) {
        var indexResults = new HashMap<Index, CompletableFuture<Boolean>>();
        var pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (var analyzerMode : AnalyzerFactory.AnalyzerMode.values()) {
            for (var similarityMode : SimilarityFactory.SimilarityMode.values()) {
                var key = new Index(analyzerMode, similarityMode);
                System.out.printf("Building index for [analyzer=%s, similarity=%s]...\n", analyzerMode, similarityMode);
                var future = CompletableFuture.supplyAsync(() -> {
                    try {
                        if (args.index()) {
                            var indexer = new Indexer(key.analyzer(), key.similarity());
                            indexer.buildIndex(args.docs(), key.path());
                            System.out.printf("Index complete for [analyzer=%s, similarity=%s]...\n", analyzerMode, similarityMode);
                        } else {
                            System.out.printf("Skipping index building for [analyzer=%s, similarity=%s]...\n", analyzerMode, similarityMode);
                        }
                        return true;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, pool);
                indexResults.put(key, future);
            }
        }

        var okay = indexResults.values().stream().allMatch(CompletableFuture::join);
        pool.shutdown();

        if (!okay) {
            System.err.println("Not all indexes completed successfully!");
            throw new RuntimeException();
        }

        return indexResults.keySet();
    }

    private static EvaluationResult evaluate(
            ArgsParser.Args.Evaluate args,
            Path outputDirectory,
            Index index,
            QueryGeneratorFactory.QueryMode queryMode,
            QueryGeneratorFactory.QueryParserMode queryParserMode) throws InterruptedException, IOException {
        var fileName = String.format("%s-%s-%s-%s", index.analyzerMode, index.similarityMode, queryMode, queryParserMode);
        var queryGenerator = QueryGeneratorFactory.getInstance(queryMode, queryParserMode);
        var searcher = new Searcher(index.analyzer(), index.similarity(), queryGenerator, fileName);
        var outputFile = Files.createTempFile(outputDirectory, fileName, ".run");
        searcher.searchTopics(index.path(), args.topics(), outputFile, args.numDocs());

        var csvPath = Files.createTempFile(outputDirectory, fileName, ".csv");
        var markdownPath = Files.createTempFile(outputDirectory, fileName, ".md");
        var command = List.of(
                "python3",
                "tools/evaluate.py",
                args.qrelsFile().toString(),
                outputFile.toString(),
                "--out_csv",
                csvPath.toString(),
                "--out_md",
                markdownPath.toString()
        );
        var processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        var process = processBuilder.start();
        var code = process.waitFor();
        if (code != 0) {
            System.err.printf("Evaluation script failed with error code [%d]!\n", code);
            System.exit(code);
        }

        var csvContent = Files.readString(csvPath);
        return EvaluationResult.parse(fileName, csvContent);
    }

    private record Index(AnalyzerFactory.AnalyzerMode analyzerMode, SimilarityFactory.SimilarityMode similarityMode) {
        Path path() {
            return Paths.get("matrix", String.format("%s-%s", analyzerMode, similarityMode));
        }

        Analyzer analyzer() {
            return AnalyzerFactory.getInstance(analyzerMode);
        }

        Similarity similarity() {
            // TODO: this is currently hardcoded, probably want a way to vary this for evaluation purposes as well.
            return SimilarityFactory.getInstance(similarityMode, Config.newBuilder().build());
        }
    }

    private record EvaluationResult(String name, double map, double precision, double nDCG) {
        static EvaluationResult parse(String name, String csvOutput) {
            var lines = csvOutput.lines().toList();
            if (lines.size() != 2) {
                System.err.printf("Encountered unexpected results file for [%s]\n----------\n%s\n-----------\n", name, csvOutput);
                System.exit(1);
            }

            var results = lines.get(1).split(",");
            var map = Double.parseDouble(results[2]);
            var precision = Double.parseDouble(results[3]);
            var nDCG = Double.parseDouble(results[4]);
            return new EvaluationResult(name, map, precision, nDCG);
        }

        private static final Comparator<EvaluationResult> COMPARATOR =
                Comparator.comparingDouble(EvaluationResult::map).reversed()
                        .thenComparingDouble(EvaluationResult::precision).reversed()
                        .thenComparingDouble(EvaluationResult::nDCG).reversed();
    }
}
