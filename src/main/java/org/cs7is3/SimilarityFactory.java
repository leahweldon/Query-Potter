package org.cs7is3;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;

public class SimilarityFactory {
    private SimilarityFactory() {
    }

    static Similarity getInstance(SimilarityMode mode, float bm25k1, float bm25b) {
        return switch (mode) {
            case BM25 -> new BM25Similarity(bm25k1, bm25b);
        };
    }

    public enum SimilarityMode {
        BM25
    }
}
