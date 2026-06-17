package io.github.ggomarighetti.jparsqlsearch.rsql.backend;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.SearchRsqlEngine;
import org.springframework.data.jpa.domain.Specification;

/** SPI that translates a validated RSQL request into a JPA specification. */
public interface RsqlBackendAdapter {
    /**
     * Compiles a request that has already passed parser, policy, and field validation.
     *
     * @param request validated compilation request
     * @param <T> entity type
     * @return deferred JPA specification
     */
    <T> Specification<T> compile(RsqlCompilationRequest<T> request);

    /**
     * Validates that this backend can execute every operator used by a definition.
     *
     * @param engine configured RSQL engine
     * @param definition definition to validate
     */
    default void validate(SearchRsqlEngine engine, SearchDefinition<?> definition) {
    }
}
