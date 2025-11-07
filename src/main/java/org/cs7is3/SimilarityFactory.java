package org.cs7is3;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;

public class SimilarityFactory {
    private SimilarityFactory() {
    }

    static Similarity getInstance(SimilarityMode mode) {
        return switch (mode) {
            case BM25 -> new BM25Similarity();
        };
    }

    public enum SimilarityMode {
        BM25
    }
}
