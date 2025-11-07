package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

public class AnalyzerFactory {
    private AnalyzerFactory() {
    }

    static Analyzer getInstance(AnalyzerMode mode) {
        return switch (mode) {
            case ENGLISH -> new EnglishAnalyzer();
        };
    }

    public enum AnalyzerMode {
        ENGLISH
    }
}
