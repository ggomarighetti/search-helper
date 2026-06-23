package io.github.ggomarighetti.jparsqlsearch.rsql.engine;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import java.util.Objects;

/** Neutral entry point for constructing RSQL engines with an explicit backend. */
public final class SearchRsqlEngines {
    private SearchRsqlEngines() {
    }

    /**
     * Creates a builder using the supplied backend.
     *
     * @param backend backend adapter selected by the composition root
     * @return configurable engine builder
     */
    public static SearchRsqlEngineBuilder builder(RsqlBackendAdapter backend) {
        return new SearchRsqlEngineBuilder(Objects.requireNonNull(backend, "backend must not be null"));
    }
}
