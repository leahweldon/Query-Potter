package org.cs7is3.config;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Config {
    @AutoValue
    public static abstract class Bm25 {
        public abstract float k1();

        public abstract float b();

        public static Bm25 create(float k1, float b) {
            return new AutoValue_Config_Bm25(k1, b);
        }
    }

    @AutoValue
    public static abstract class Dirichlet {
        public abstract float mu();

        public static Dirichlet create(float mu) {
            return new AutoValue_Config_Dirichlet(mu);
        }
    }

    public abstract Bm25 bm25();

    public abstract Dirichlet dirichlet();

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public static abstract class Builder {
        public abstract Builder bm25(Bm25 bm25);

        public abstract Builder dirichlet(Dirichlet dirichlet);

        public abstract Config build();
    }

    public static Builder newBuilder() {
        return new AutoValue_Config.Builder()
                .bm25(Bm25.create(Defaults.BM25_K1, Defaults.BM25_B))
                .dirichlet(Dirichlet.create(Defaults.DIRICHLET_MU));
    }
}
