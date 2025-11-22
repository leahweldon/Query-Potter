package org.cs7is3;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;

public class SimilarityFactory {
    private SimilarityFactory() {
    }

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
        };
    }

    public enum SimilarityMode {
        BM25,
        DIRICHLET_BASE
    }
}
