package io.github.ggomarighetti.jparsqlsearch.rsql.backend;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import java.util.Objects;
import org.springframework.core.convert.ConversionService;

/**
 * Minimal state supplied to a backend when validating a search definition.
 *
 * @param definition definition being validated
 * @param operators configured logical operator registry
 * @param jpaOperators configured custom JPA bindings
 * @param conversionService conversion service shared with compilation
 */
public record RsqlBackendValidationContext(
        SearchDefinition<?> definition,
        RsqlOperatorRegistry operators,
        RsqlJpaOperatorRegistry jpaOperators,
        ConversionService conversionService) {
    /** Validates required context components. */
    public RsqlBackendValidationContext {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(operators, "operators must not be null");
        Objects.requireNonNull(jpaOperators, "jpaOperators must not be null");
        Objects.requireNonNull(conversionService, "conversionService must not be null");
    }
}
