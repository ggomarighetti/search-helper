package io.github.ggomarighetti.jparsqlsearch.rsql.backend;

import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
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
     * @param context minimal backend validation state
     */
    default void validate(RsqlBackendValidationContext context) {
    }
}
