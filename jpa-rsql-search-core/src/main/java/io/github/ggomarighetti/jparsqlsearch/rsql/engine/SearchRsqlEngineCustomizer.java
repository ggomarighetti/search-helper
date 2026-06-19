package io.github.ggomarighetti.jparsqlsearch.rsql.engine;

/** Customizes the auto-configured RSQL engine builder. */
@FunctionalInterface
public interface SearchRsqlEngineCustomizer {
    /**
     * Applies engine customizations.
     *
     * @param builder builder to customize
     */
    void customize(SearchRsqlEngineBuilder builder);
}
