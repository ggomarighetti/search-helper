package io.github.ggomarighetti.jparsqlsearch.compile;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.Node;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlValidationError;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.filter.SearchFiltering;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.definition.validation.SearchDefinitionValidator;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;

final class RsqlSearchGuard {
    private static final String DEFINITION_MUST_NOT_BE_NULL = "definition must not be null";

    private final SearchRsqlEngine engine;
    private final SearchPolicy policy;
    private final List<SearchDefinitionValidator> definitionValidators;
    private final Map<SearchDefinition<?>, Boolean> validatedDefinitions =
            Collections.synchronizedMap(new WeakHashMap<>());

    public RsqlSearchGuard(SearchRsqlEngine engine) {
        this(engine, SearchPolicy.defaults());
    }

    public RsqlSearchGuard(SearchRsqlEngine engine, SearchPolicy policy) {
        this(engine, policy, List.of());
    }

    public RsqlSearchGuard(
            SearchRsqlEngine engine,
            SearchPolicy policy,
            Collection<? extends SearchDefinitionValidator> definitionValidators) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.definitionValidators = List.copyOf(
                Objects.requireNonNull(definitionValidators, "definitionValidators must not be null"));
    }

    public SearchPolicy policy() {
        return policy;
    }

    public SearchRsqlEngine engine() {
        return engine;
    }

    public void validateDefinition(SearchDefinition<?> definition) {
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        synchronized (validatedDefinitions) {
            if (validatedDefinitions.containsKey(definition)) {
                return;
            }
            for (SearchDefinitionValidator validator : definitionValidators) {
                validator.validate(definition);
            }
            engine.validate(definition);
            validatedDefinitions.put(definition, Boolean.TRUE);
        }
    }

    public <T> Specification<T> specification(String rsql, SearchDefinition<T> definition) {
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        SearchPolicy effectivePolicy = effectivePolicy(definition);
        SearchProtectionContext protection =
                new SearchProtectionContext(effectivePolicy, SearchProtectionContext.Mode.PAGE);
        return specification(rsql, definition, protection);
    }

    public <T> Specification<T> specification(
            String rsql,
            SearchDefinition<T> definition,
            SearchProtectionContext protection) {
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(protection, "protection must not be null");
        SearchPolicy effectivePolicy = protection.policy();

        preflight(rsql, effectivePolicy.rsql());
        validateDefinition(definition);
        RsqlAst ast = engine.parse(rsql);
        List<RsqlValidationError> errors;
        try {
            errors = new RsqlRulesValidator(
                            definition,
                            engine.conversionService(),
                            effectivePolicy.rsql(),
                            protection,
                            engine.operators())
                    .validate(ast);
        } catch (RsqlFilterValidationException
                | SearchProtectionException
                | SearchDefinitionValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SearchDefinitionValidationException(
                    SearchDefinitionValidationException.RSQL_CONFIGURATION_INVALID,
                    "SearchDefinition RSQL rules or converters could not be evaluated.",
                    exception);
        }
        if (!errors.isEmpty()) {
            throw rulesForbidden(errors);
        }
        boolean distinct = requiresDistinct(ast.node(), definition);
        if (distinct) {
            protection.recordDistinct();
        }
        protection.completeFilter();
        try {
            return guardDeferredFailures(engine.compile(new RsqlCompilationRequest<>(
                    rsql,
                    ast,
                    definition,
                    distinct,
                    engine.conversionService(),
                    engine.operators(),
                    engine.jpaOperators())));
        } catch (RuntimeException exception) {
            throw new RsqlFilterValidationException(
                    RsqlFilterValidationException.RULES_FORBIDDEN,
                    "RSQL filter could not be compiled to a JPA Specification.",
                    exception);
        }
    }

    private SearchPolicy effectivePolicy(SearchDefinition<?> definition) {
        return definition.effectiveLimits(policy);
    }

    private static boolean requiresDistinct(Node ast, SearchDefinition<?> definition) {
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(ast);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node instanceof ComparisonNode comparison) {
                if (definition.field(comparison.getSelector())
                        .map(SearchField::filtering)
                        .filter(SearchFiltering::requiresDistinct)
                        .isPresent()) {
                    return true;
                }
            } else if (node instanceof LogicalNode logical) {
                List<Node> children = logical.getChildren();
                for (Node child : children) {
                    stack.push(child);
                }
            }
        }
        return false;
    }

    private static <T> Specification<T> guardDeferredFailures(Specification<T> specification) {
        Objects.requireNonNull(specification, "compiled specification must not be null");
        return (root, query, criteriaBuilder) -> {
            try {
                return specification.toPredicate(root, query, criteriaBuilder);
            } catch (RsqlFilterValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new RsqlFilterValidationException(
                        RsqlFilterValidationException.RULES_FORBIDDEN,
                        "RSQL filter could not be compiled to a JPA Predicate.",
                        exception);
            }
        };
    }

    private void preflight(String rsql, SearchPolicy.Rsql limits) {
        if (rsql == null) {
            throw limitExceeded();
        }
        if (exceedsLength(rsql, limits.maxLength())
                || exceedsParenthesesDepth(rsql, limits.maxParenthesesDepth())) {
            throw limitExceeded();
        }
    }

    private static boolean exceedsLength(String rsql, int maxLength) {
        return rsql.length() > maxLength;
    }

    private static boolean exceedsParenthesesDepth(String rsql, int maxParenthesesDepth) {
        int depth = 0;
        RsqlPreflightScanner scanner = new RsqlPreflightScanner();
        for (int index = 0; index < rsql.length(); index++) {
            char current = rsql.charAt(index);
            if (scanner.consumeQuoted(current)) {
                continue;
            }

            depth = adjustedParenthesesDepth(depth, current);
            if (depth > maxParenthesesDepth) {
                return true;
            }
        }
        return false;
    }

    private static int adjustedParenthesesDepth(int depth, char current) {
        if (current == '(') {
            return depth + 1;
        }
        if (current == ')' && depth > 0) {
            return depth - 1;
        }
        return depth;
    }

    private RsqlFilterValidationException rulesForbidden(List<RsqlValidationError> errors) {
        return new RsqlFilterValidationException(
                RsqlFilterValidationException.RULES_FORBIDDEN,
                "RSQL filter failed semantic validation with %d error(s).".formatted(errors.size()),
                errors);
    }

    private RsqlFilterValidationException limitExceeded() {
        return new RsqlFilterValidationException(
                RsqlFilterValidationException.LIMIT_EXCEEDED,
                "RSQL filter exceeds configured safety limits.");
    }

    private static final class RsqlPreflightScanner {
        private char quote;
        private boolean escaped;

        private boolean consumeQuoted(char current) {
            if (escaped) {
                escaped = false;
                return true;
            }
            if (isQuoted()) {
                consumeWithinQuote(current);
                return true;
            }
            if (isQuote(current)) {
                quote = current;
                return true;
            }
            return false;
        }

        private boolean isQuoted() {
            return quote != 0;
        }

        private void consumeWithinQuote(char current) {
            if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                quote = 0;
            }
        }

        private static boolean isQuote(char current) {
            return current == '\'' || current == '"';
        }
    }
}
