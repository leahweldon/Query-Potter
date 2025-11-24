package org.cs7is3.config;

import org.cs7is3.AnalyzerFactory;
import org.cs7is3.QueryGeneratorFactory;
import org.cs7is3.SimilarityFactory;

public class Defaults {
    public static final int NUMBER_OF_DOCUMENTS = 1_000;

    // Tuned analyzer / similarity / query settings:
    public static final AnalyzerFactory.AnalyzerMode ANALYZER_MODE = AnalyzerFactory.AnalyzerMode.ENGLISH_STOP_WORDS;

    public static final SimilarityFactory.SimilarityMode SIMILARITY_MODE = SimilarityFactory.SimilarityMode.BM25;

    public static final QueryGeneratorFactory.QueryMode QUERY_MODE = QueryGeneratorFactory.QueryMode.PRF_TITLE_DESC_NARRATIVE_CLEANED;

    public static final QueryGeneratorFactory.QueryParserMode QUERY_PARSER = QueryGeneratorFactory.QueryParserMode.WEAK_TITLE_STRONG_CONTENT;

    // Tuned BM25 parameters (used when no CLI override is provided):
    public static final float BM25_K1 = 0.5f;
    public static final float BM25_B = 1.0f;

    // Keep Dirichlet default for completeness (not used in best config)
    public static final float DIRICHLET_MU = 2000.0f;

    private Defaults() {
    }
}