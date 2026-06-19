package io.github.ggomarighetti.jparsqlsearch.rsql;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import java.util.Objects;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.Assert;

/**
 * Validated input supplied to an {@link io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter}.
 *
 * @param <T> entity type
 * @param rsql original non-blank filter
 * @param ast parsed and normalized AST
 * @param definition field and capability definition
 * @param distinct whether the query must eliminate duplicates
 * @param conversionService conversion service used during validation and execution
 * @param operators configured operator registry
 * @param jpaOperators configured custom JPA bindings
 */
public record RsqlCompilationRequest<T>(
        String rsql,
        RsqlAst ast,
        SearchDefinition<T> definition,
        boolean distinct,
        ConversionService conversionService,
        RsqlOperatorRegistry operators,
        RsqlJpaOperatorRegistry jpaOperators) {
    /** Validates required request components. */
    public RsqlCompilationRequest {
        Assert.hasText(rsql, "rsql must not be blank");
        Objects.requireNonNull(ast, "ast must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(conversionService, "conversionService must not be null");
        Objects.requireNonNull(operators, "operators must not be null");
        Objects.requireNonNull(jpaOperators, "jpaOperators must not be null");
    }
}
