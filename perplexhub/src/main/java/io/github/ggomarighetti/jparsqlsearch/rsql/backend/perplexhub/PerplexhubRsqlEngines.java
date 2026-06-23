package io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub;

import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngineBuilder;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngines;

/** Standalone composition root for engines backed by Perplexhub. */
public final class PerplexhubRsqlEngines {
    private PerplexhubRsqlEngines() {
    }

    /**
     * Creates a builder with the default Perplexhub backend.
     *
     * @return configurable engine builder
     */
    public static SearchRsqlEngineBuilder builder() {
        return SearchRsqlEngines.builder(new PerplexhubRsqlBackendAdapter());
    }

    /**
     * Creates an engine with the default parser, operators, conversion and backend.
     *
     * @return default Perplexhub-backed engine
     */
    public static SearchRsqlEngine defaults() {
        return builder().build();
    }
}
