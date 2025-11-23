package org.cs7is3;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.cs7is3.config.Config;

public class SimilarityFactory {
    private SimilarityFactory() {
    }

<<<<<<< HEAD
    static Similarity getInstance(SimilarityMode mode) {
        return switch (mode) {
            case BM25 -> new BM25Similarity();
            case DIRICHLET_BASE -> new LMDirichletSimilarity();
        };
    }

    static Similarity getInstance(SimilarityMode mode, float bm25k1, float bm25b) {
        return switch (mode) {
            case BM25 -> new BM25Similarity(bm25k1, bm25b);
            case DIRICHLET_BASE -> new LMDirichletSimilarity();
=======
    static Similarity getInstance(SimilarityMode mode, Config config) {
        return switch (mode) {
            case BM25 -> new BM25Similarity(config.bm25().k1(), config.bm25().b());
            case DIRICHLET -> new LMDirichletSimilarity(config.dirichlet().mu());
>>>>>>> main
        };
    }

    public enum SimilarityMode {
<<<<<<< HEAD
        BM25,
        DIRICHLET_BASE
=======
        BM25, DIRICHLET
>>>>>>> main
    }
}
