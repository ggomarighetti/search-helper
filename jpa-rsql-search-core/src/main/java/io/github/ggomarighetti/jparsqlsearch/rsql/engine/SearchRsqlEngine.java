package io.github.ggomarighetti.jparsqlsearch.rsql.engine;

import cz.jirutka.rsql.parser.RSQLParserException;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendValidationContext;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.parser.RsqlParserFactory;
import java.util.Objects;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;

/**
 * Configurable RSQL parser and backend compiler.
 *
 * <p>The high-level {@code SearchCompiler} applies request protection before invoking this
 * engine. Direct callers are responsible for applying equivalent policy checks.
 */
public final class SearchRsqlEngine {
    private final RsqlOperatorRegistry operators;
    private final RsqlJpaOperatorRegistry jpaOperators;
    private final RsqlParserFactory parserFactory;
    private final RsqlBackendAdapter backend;
    private final ConversionService conversionService;

    SearchRsqlEngine(
            RsqlOperatorRegistry operators,
            RsqlJpaOperatorRegistry jpaOperators,
            RsqlParserFactory parserFactory,
            RsqlBackendAdapter backend,
            ConversionService conversionService) {
        this.operators = Objects.requireNonNull(operators, "operators must not be null");
        this.jpaOperators = Objects.requireNonNull(jpaOperators, "jpaOperators must not be null");
        this.parserFactory = Objects.requireNonNull(parserFactory, "parserFactory must not be null");
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService must not be null");
    }

    /**
     * Returns the registered operators.
     *
     * @return immutable operator registry
     */
    public RsqlOperatorRegistry operators() {
        return operators;
    }

    /**
     * Returns the conversion service shared by validation and backend compilation.
     *
     * @return configured conversion service
     */
    public ConversionService conversionService() {
        return conversionService;
    }

    /**
     * Returns custom JPA operator bindings.
     *
     * @return immutable JPA operator registry
     */
    public RsqlJpaOperatorRegistry jpaOperators() {
        return jpaOperators;
    }

    /**
     * Parses RSQL into the library's immutable AST.
     *
     * @param rsql RSQL expression
     * @return parsed AST
     * @throws RsqlFilterValidationException when parsing fails
     */
    public RsqlAst parse(String rsql) {
        try {
            return parserFactory.parse(rsql, operators);
        } catch (IllegalArgumentException | RSQLParserException exception) {
            throw new RsqlFilterValidationException(
                    RsqlFilterValidationException.PARSE_ERROR,
                    "RSQL filter could not be parsed.",
                    exception);
        }
    }

    /**
     * Compiles a validated RSQL request with the configured backend.
     *
     * @param request backend-neutral compilation request
     * @param <T> entity type
     * @return JPA specification
     */
    public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
        return backend.compile(request);
    }

    /**
     * Verifies that a search definition is compatible with this engine.
     *
     * @param definition definition to validate
     * @throws SearchDefinitionValidationException when an operator or backend contract is invalid
     */
    public void validate(SearchDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        for (SearchField<?> field : definition.fields().values()) {
            if (!field.filtering().enabled()) {
                continue;
            }
            field.filtering().operators().keySet().forEach(operator -> validateOperator(field, operator));
        }
        backend.validate(new RsqlBackendValidationContext(
                definition,
                operators,
                jpaOperators,
                conversionService));
    }

    private void validateOperator(SearchField<?> field, RsqlOperator operator) {
        RsqlOperatorDescriptor descriptor = operators.descriptor(operator).orElseThrow(() ->
                invalidDefinition(
                        SearchDefinitionValidationException.RSQL_OPERATOR_NOT_REGISTERED,
                        "operator '%s' used by selector '%s' is not registered"
                        .formatted(operator, field.selector())));
        FilterOperator<?> filterOperator = field.filtering().operators().get(operator);
        Class<?> argumentType = descriptor.argumentType()
                .<Class<?>>map(type -> type)
                .orElse(field.filtering().type());
        if (!filterOperator.argumentType().equals(argumentType)) {
            throw invalidDefinition(
                    SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH,
                    "selector '%s' operator '%s' validates '%s' but the engine executes '%s'"
                            .formatted(
                                    field.selector(),
                                    operator,
                                    filterOperator.argumentType().getName(),
                                    argumentType.getName()));
        }
        if (!conversionService.canConvert(String.class, argumentType)
                && !String.class.equals(argumentType)) {
            throw invalidDefinition(
                    SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH,
                    "selector '%s' operator '%s' argument type '%s' cannot be converted from String"
                    .formatted(field.selector(), operator, argumentType.getName()));
        }
    }

    private SearchDefinitionValidationException invalidDefinition(String code, String message) {
        return new SearchDefinitionValidationException(code, message);
    }
}
