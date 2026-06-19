package io.github.ggomarighetti.jparsqlsearch.filter;

import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.Assert;
import org.springframework.core.convert.ConversionService;

/**
 * Filtering policy for one declared field.
 *
 * @param <T> exposed field type
 */
public final class SearchFiltering<T> implements AutoCloseable {
    private static final String OPERATOR_MUST_NOT_BE_NULL = "operator must not be null";

    private static final SearchFiltering<?> DISABLED =
            new SearchFiltering<>(false, null, Object.class, false, SearchPath.Topology.none(), Map.of());

    private final boolean enabled;
    private final String path;
    private final Class<T> type;
    private final boolean requiresDistinct;
    private final SearchPath.Topology topology;
    private final Map<RsqlOperator, FilterOperator<?>> operators;

    private SearchFiltering(
            boolean enabled,
            String path,
            Class<T> type,
            boolean requiresDistinct,
            SearchPath.Topology topology,
            Map<RsqlOperator, FilterOperator<?>> operators) {
        this.enabled = enabled;
        this.path = path;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.requiresDistinct = requiresDistinct;
        this.topology = Objects.requireNonNull(topology, "topology must not be null");
        this.operators = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(operators, "operators must not be null")));
    }

    /**
     * Returns disabled filtering.
     *
     * @param <T> exposed field type
     * @return reusable disabled filtering policy
     */
    @SuppressWarnings("unchecked")
    public static <T> SearchFiltering<T> disabled() {
        return (SearchFiltering<T>) DISABLED;
    }

    /**
     * Creates a filtering builder.
     *
     * @param <T> exposed field type
     * @return new filtering builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Reports whether filtering is enabled.
     *
     * @return whether filtering is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the filtering path.
     *
     * @return effective JPA path, or {@code null} when disabled
     */
    public String path() {
        return path;
    }

    /**
     * Returns the field type.
     *
     * @return exposed field type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Reports collection traversal.
     *
     * @return whether this path traverses a collection
     */
    public boolean requiresDistinct() {
        return requiresDistinct;
    }

    /**
     * Returns path topology.
     *
     * @return immutable path topology
     */
    public SearchPath.Topology topology() {
        return topology;
    }

    /**
     * Returns allowed operators.
     *
     * @return immutable allowed operators and validation rules
     */
    public Map<RsqlOperator, FilterOperator<?>> operators() {
        return operators;
    }

    /**
     * Tests whether an operator is whitelisted.
     *
     * @param operator logical operator
     * @return {@code true} when allowed
     */
    public boolean allows(RsqlOperator operator) {
        Objects.requireNonNull(operator, OPERATOR_MUST_NOT_BE_NULL);
        return operators.containsKey(operator);
    }

    /**
     * Converts and validates arguments for an allowed operator.
     *
     * @param operator logical operator
     * @param arguments raw arguments
     * @param conversionService conversion service shared with execution
     * @return {@code true} when the operator is allowed and arguments are valid
     */
    public boolean accepts(RsqlOperator operator, List<String> arguments, ConversionService conversionService) {
        Objects.requireNonNull(operator, OPERATOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(conversionService, "conversionService must not be null");
        if (!operators.containsKey(operator)) {
            return false;
        }
        return operators.get(operator).accepts(arguments, conversionService);
    }

    /** Releases validator resources owned by enabled operator declarations. */
    @Override
    public void close() {
        operators.values().forEach(FilterOperator::close);
    }

    /**
     * Builder for one field's filtering whitelist.
     *
     * @param <T> exposed field type
     */
    public static final class Builder<T> {
        private String path;
        private boolean includeDefaults;
        private final Map<RsqlOperator, OperatorDeclaration<?>> operators = new LinkedHashMap<>();
        private final Set<RsqlOperator> deniedOperators = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * Overrides the filtering path.
         *
         * @param path dot-separated Java/JPA path
         * @return this builder
         */
        public Builder<T> path(String path) {
            Assert.hasText(path, "path must not be blank");
            this.path = path;
            return this;
        }

        /**
         * Includes the restrictive default operator profile for the field type.
         *
         * <p>Explicit declarations override inherited defaults, while
         * {@link #deny(RsqlOperator...)} removes them.
         *
         * @return this builder
         */
        public Builder<T> withDefaults() {
            includeDefaults = true;
            return this;
        }

        /**
         * Allows operators without additional rules.
         *
         * @param operators operators to allow
         * @return this builder
         */
        public Builder<T> allow(RsqlOperator... operators) {
            requireOperators(operators);
            for (RsqlOperator operator : operators) {
                allow(operator);
            }
            return this;
        }

        /**
         * Allows one operator without additional rules.
         *
         * @param operator operator to allow
         * @return this builder
         */
        public Builder<T> allow(RsqlOperator operator) {
            return allow(operator, ignored -> {});
        }

        /**
         * Removes operators from the current whitelist.
         *
         * <p>Removing an operator that is not present has no effect, which allows the
         * same customization to be applied to different default type profiles.
         *
         * @param operators operators to remove
         * @return this builder
         */
        public Builder<T> deny(RsqlOperator... operators) {
            requireOperators(operators);
            for (RsqlOperator operator : operators) {
                this.operators.remove(operator);
                deniedOperators.add(operator);
            }
            return this;
        }

        /**
         * Allows an operator and customizes rules using the field type.
         *
         * @param operator operator to allow
         * @param customizer argument-rule customizer
         * @return this builder
         */
        public Builder<T> allow(
                RsqlOperator operator, Consumer<FilterOperator.Builder<T>> customizer) {
            Objects.requireNonNull(operator, OPERATOR_MUST_NOT_BE_NULL);
            Objects.requireNonNull(customizer, "customizer must not be null");

            FilterOperator.Builder<T> builder = FilterOperator.builder(operator);
            customizer.accept(builder);
            deniedOperators.remove(operator);
            if (operators.putIfAbsent(operator, new OperatorDeclaration<>(null, builder)) != null) {
                throw new IllegalArgumentException("operator '%s' is already declared".formatted(operator));
            }
            return this;
        }

        /**
         * Allows an operator with an explicit conversion type.
         *
         * @param operator operator to allow
         * @param argumentType conversion and validation type
         * @param customizer argument-rule customizer
         * @param <A> argument type
         * @return this builder
         */
        public <A> Builder<T> allow(
                RsqlOperator operator,
                Class<A> argumentType,
                Consumer<FilterOperator.Builder<A>> customizer) {
            Objects.requireNonNull(operator, OPERATOR_MUST_NOT_BE_NULL);
            Objects.requireNonNull(argumentType, "argumentType must not be null");
            Objects.requireNonNull(customizer, "customizer must not be null");

            FilterOperator.Builder<A> builder = FilterOperator.builder(operator);
            customizer.accept(builder);
            deniedOperators.remove(operator);
            if (operators.putIfAbsent(operator, new OperatorDeclaration<>(argumentType, builder)) != null) {
                throw new IllegalArgumentException("operator '%s' is already declared".formatted(operator));
            }
            return this;
        }

        /**
         * Resolves defaults and validates the effective path.
         *
         * @param entity root entity type
         * @param selector public selector
         * @param type exposed field type
         * @param defaultPath shared field path
         * @param pathLimits path depth limits
         * @return immutable filtering policy
         */
        public SearchFiltering<T> build(
                Class<?> entity,
                String selector,
                Class<T> type,
                String defaultPath,
                SearchPolicy.Paths pathLimits) {
            Map<RsqlOperator, OperatorDeclaration<?>> resolvedOperators = resolveOperators(selector, type);
            if (resolvedOperators.isEmpty()) {
                throw new IllegalArgumentException(
                        "selector '%s' filtering must declare at least one allowed operator".formatted(selector));
            }
            String resolvedPath = path == null ? defaultPath : path;
            SearchPath.Metadata pathMetadata = SearchPath.metadata(entity, selector, resolvedPath, type, pathLimits);
            SearchPath.Topology topology = SearchPath.topology(entity, selector, resolvedPath, pathLimits);
            Map<RsqlOperator, FilterOperator<?>> built = new LinkedHashMap<>();
            resolvedOperators.forEach((operator, declaration) -> built.put(operator, declaration.build(type)));
            return new SearchFiltering<>(
                    true,
                    resolvedPath,
                    type,
                    pathMetadata.traversesCollection() || pathMetadata.collectionValued(),
                    topology,
                    built);
        }

        private Map<RsqlOperator, OperatorDeclaration<?>> resolveOperators(String selector, Class<T> type) {
            Map<RsqlOperator, OperatorDeclaration<?>> resolved = new LinkedHashMap<>();
            if (includeDefaults) {
                Set<RsqlOperator> defaults = DefaultFilterOperators.forType(type);
                if (defaults.isEmpty()) {
                    throw new SearchDefinitionValidationException(
                            SearchDefinitionValidationException.DEFAULT_OPERATORS_UNSUPPORTED_TYPE,
                            "selector '%s' type '%s' has no default filter operator profile"
                                    .formatted(selector, type.getName()));
                }
                defaults.stream()
                        .filter(operator -> !deniedOperators.contains(operator))
                        .forEach(operator -> resolved.put(
                                operator,
                                new OperatorDeclaration<>(null, FilterOperator.builder(operator))));
            }
            resolved.putAll(operators);
            return resolved;
        }

        private static void requireOperators(RsqlOperator[] operators) {
            Assert.notEmpty(operators, "operators must not be empty");
            for (RsqlOperator operator : operators) {
                Objects.requireNonNull(operator, "operators must not contain null values");
            }
        }

        private record OperatorDeclaration<A>(Class<A> explicitType, FilterOperator.Builder<A> builder) {
            @SuppressWarnings("unchecked")
            private FilterOperator<A> build(Class<?> defaultType) {
                Class<A> resolvedType = explicitType == null ? (Class<A>) defaultType : explicitType;
                return builder.build(resolvedType);
            }
        }
    }
}
