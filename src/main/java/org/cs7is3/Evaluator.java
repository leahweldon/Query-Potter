package org.cs7is3;

import org.cs7is3.config.Config;
import org.cs7is3.config.Defaults;
import org.cs7is3.parsing.ArgsParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Evaluator {
    private Evaluator() {
    }

    public static void evaluate(ArgsParser.Args.Evaluate args) throws IOException, InterruptedException {
        var outputDirectory = Files.createTempDirectory("tmp");
        System.out.printf("Opened temporary directory at [%s]...\n", outputDirectory.toString());
        outputDirectory.toFile().deleteOnExit();
        var results = new ArrayList<EvaluationResult>();

        for (var analyzerMode : AnalyzerFactory.AnalyzerMode.values()) {
            var analyzer = AnalyzerFactory.getInstance(analyzerMode);

            for (var similarityMode : SimilarityFactory.SimilarityMode.values()) {
                // TODO: this is currently hardcoded, probably want a way to vary this for evaluation purposes as well.
                var similarity = SimilarityFactory.getInstance(similarityMode, Config.newBuilder().build());

                var indexer = new Indexer(analyzer, similarity);
                var indexPath = Paths.get("matrix", String.format("%s-%s", analyzerMode, similarityMode));
                System.out.printf("Building index for [analyzer=%s, similarity=%s]...\n", analyzerMode, similarityMode);
                indexer.buildIndex(args.docs(), indexPath);

                for (var queryMode : QueryGeneratorFactory.QueryMode.values()) {
                    for (var queryParserMode : QueryGeneratorFactory.QueryParserMode.values()) {
                        var fileName = String.format("%s-%s-%s-%s", analyzerMode, similarityMode, queryMode, queryParserMode);
                        var queryGenerator = QueryGeneratorFactory.getInstance(queryMode, queryParserMode);
                        var searcher = new Searcher(analyzer, similarity, queryGenerator, fileName);
                        var outputFile = Files.createTempFile(outputDirectory, fileName, ".run");
                        searcher.searchTopics(indexPath, args.topics(), outputFile, args.numDocs());

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
                        results.add(EvaluationResult.parse(fileName, csvContent));
                    }
                }
            }
        }

        if (results.isEmpty()) {
            return;
        }

        results.sort(EvaluationResult.COMPARATOR);
        System.out.println("-------------------------\nCompleted evaluating all approaches!\n--------------------");
        results.forEach(System.out::println);
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
