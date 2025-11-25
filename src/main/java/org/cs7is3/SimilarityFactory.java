package org.cs7is3;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.cs7is3.config.Config;

public class SimilarityFactory {
    private SimilarityFactory() {
    }

    static Similarity getInstance(SimilarityMode mode, Config config) {
        return switch (mode) {
            case BM25 -> new BM25Similarity(config.bm25().k1(), config.bm25().b());
            case DIRICHLET -> new LMDirichletSimilarity(config.dirichlet().mu());
        };
    }

    public enum SimilarityMode {
        BM25, DIRICHLET
    }
}