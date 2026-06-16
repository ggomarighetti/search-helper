package io.github.ggomarighetti.searchhelper.definition;

import io.github.ggomarighetti.searchhelper.page.SearchPaging;
import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import io.github.ggomarighetti.searchhelper.query.SearchQuery;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.Assert;

/**
 * Immutable search policy for one entity/search use case.
 *
 * @param <T> entity type compiled by this definition
 */
public final class SearchDefinition<T> {
    private static final String CUSTOMIZER_MUST_NOT_BE_NULL = "customizer must not be null";

    private final Class<T> entity;
    private final Map<String, SearchField<?>> fields;
    private final SearchPaging paging;
    private final SearchQuery<T> query;
    private final SearchPolicy limits;
    private final UnaryOperator<SearchPolicy> limitsOverlay;

    private SearchDefinition(
            Class<T> entity,
            Map<String, SearchField<?>> fields,
            SearchPaging paging,
            SearchQuery<T> query,
            SearchPolicy limits,
            UnaryOperator<SearchPolicy> limitsOverlay) {
        this.entity = entity;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.paging = Objects.requireNonNull(paging, "paging must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.limits = limits;
        this.limitsOverlay = limitsOverlay;
    }

    /**
     * Starts a definition using default path limits.
     *
     * @return entity selection step
     */
    public static EntityStep builder() {
        return new EntityStep(SearchPolicy.defaults());
    }

    /**
     * Starts a definition using path limits from a policy.
     *
     * @param policy policy supplying definition-time path limits
     * @return entity selection step
     */
    public static EntityStep builder(SearchPolicy policy) {
        return new EntityStep(policy);
    }

    /**
     * Looks up a declared public selector.
     *
     * @param selector public selector
     * @return declared field when present
     */
    public Optional<SearchField<?>> field(String selector) {
        return Optional.ofNullable(fields.get(selector));
    }

    /**
     * Returns all declared fields.
     *
     * @return immutable fields keyed by public selector
     */
    public Map<String, SearchField<?>> fields() {
        return fields;
    }

    /**
     * Returns the root entity type.
     *
     * @return root entity type
     */
    public Class<T> entity() {
        return entity;
    }

    /**
     * Returns paging capabilities.
     *
     * @return paging capability declaration
     */
    public SearchPaging paging() {
        return paging;
    }

    /**
     * Returns free-text query capabilities.
     *
     * @return free-text query capability declaration
     */
    public SearchQuery<T> query() {
        return query;
    }

    /**
     * Returns materialized local limits.
     *
     * @return materialized local policy when definition limits were declared
     */
    public Optional<SearchPolicy> limits() {
        return Optional.ofNullable(limits);
    }

    /**
     * Resolves the policy used for one request.
     *
     * <p>Customizer-based limits overlay only explicitly changed values on the
     * supplied global policy. An explicit {@link SearchPolicy} replaces it.
     *
     * @param globalLimits global compiler policy
     * @return effective immutable policy
     */
    public SearchPolicy effectiveLimits(SearchPolicy globalLimits) {
        Objects.requireNonNull(globalLimits, "globalLimits must not be null");
        if (limitsOverlay != null) {
            return limitsOverlay.apply(globalLimits);
        }
        if (limits != null) {
            return limits;
        }
        return globalLimits;
    }

    /**
     * Returns filtering path aliases.
     *
     * @return immutable public selector to filtering path mapping
     */
    public Map<String, String> filteringPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        fields.values().stream()
                .filter(field -> field.filtering().enabled())
                .forEach(field -> paths.put(field.selector(), field.filtering().path()));
        return Collections.unmodifiableMap(paths);
    }

    /**
     * Returns operators used by this definition.
     *
     * @return immutable union of operators allowed by all filterable fields
     */
    public Set<RsqlOperator> filteringOperators() {
        Set<RsqlOperator> operators = new LinkedHashSet<>();
        fields.values().stream()
                .filter(field -> field.filtering().enabled())
                .forEach(field -> operators.addAll(field.filtering().operators().keySet()));
        return Collections.unmodifiableSet(operators);
    }

    /**
     * Returns sorting path aliases.
     *
     * @return immutable public selector to sorting path mapping
     */
    public Map<String, String> sortingPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        fields.values().stream()
                .filter(field -> field.sorting().enabled())
                .forEach(field -> paths.put(field.selector(), field.sorting().path()));
        return Collections.unmodifiableMap(paths);
    }

    /**
     * Returns sorting directions.
     *
     * @return immutable allowed sorting directions by selector
     */
    public Map<String, Set<Direction>> sortingDirections() {
        Map<String, Set<Direction>> directions = new LinkedHashMap<>();
        fields.values().stream()
                .filter(field -> field.sorting().enabled())
                .forEach(field -> directions.put(field.selector(), field.sorting().directions()));
        return Collections.unmodifiableMap(directions);
    }

    /** First DSL step that requires the root entity type. */
    public static final class EntityStep {
        private final SearchPolicy policy;

        private EntityStep(SearchPolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy must not be null");
        }

        /**
         * Selects the root entity type.
         *
         * @param entity entity type
         * @param <T> entity type
         * @return typed definition builder
         */
        public <T> Builder<T> entity(Class<T> entity) {
            Assert.notNull(entity, "entity must not be null");
            return new Builder<>(entity, policy);
        }
    }

    /**
     * Builder for an immutable search definition.
     *
     * @param <T> entity type
     */
    public static final class Builder<T> {
        private final Fields<T> fields;
        private final SearchPolicy baseLimits;
        private SearchPaging.Builder paging;
        private SearchQuery<T> query;
        private SearchPolicy limits;
        private Consumer<SearchPolicy.Builder> limitsCustomizer;

        private Builder(Class<T> entity, SearchPolicy baseLimits) {
            this.fields = new Fields<>(entity);
            this.baseLimits = Objects.requireNonNull(baseLimits, "baseLimits must not be null");
        }

        /**
         * Declares public fields and their capabilities.
         *
         * @param customizer fields DSL customizer
         * @return this builder
         */
        public Builder<T> fields(Consumer<Fields<T>> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(fields);
            return this;
        }

        /**
         * Enables paging with default rules.
         *
         * @return this builder with default paging enabled
         */
        public Builder<T> paging() {
            return paging(ignored -> {});
        }

        /**
         * Enables and customizes paging.
         *
         * @param customizer paging customizer
         * @return this builder
         */
        public Builder<T> paging(Consumer<SearchPaging.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            if (paging != null) {
                throw new IllegalArgumentException("paging is already declared");
            }
            paging = SearchPaging.builder();
            customizer.accept(paging);
            return this;
        }

        /**
         * Enables and customizes free-text query.
         *
         * @param customizer query customizer
         * @return this builder
         */
        public Builder<T> query(Consumer<SearchQuery.Builder<T>> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            ensureQueryIsNotDeclared();
            SearchQuery.Builder<T> builder = SearchQuery.builder();
            customizer.accept(builder);
            query = builder.build();
            return this;
        }

        /**
         * Uses a prebuilt query policy.
         *
         * @param query query policy
         * @return this builder
         */
        public Builder<T> query(SearchQuery<T> query) {
            ensureQueryIsNotDeclared();
            this.query = Objects.requireNonNull(query, "query must not be null");
            return this;
        }

        /**
         * Records partial policy overrides to apply over the compiler policy.
         *
         * @param customizer policy override customizer
         * @return this builder
         */
        public Builder<T> limits(Consumer<SearchPolicy.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            ensureLimitsAreNotDeclared();
            limitsCustomizer = customizer;
            return this;
        }

        /**
         * Replaces the compiler policy for this definition.
         *
         * @param limits complete local policy
         * @return this builder
         */
        public Builder<T> limits(SearchPolicy limits) {
            ensureLimitsAreNotDeclared();
            this.limits = Objects.requireNonNull(limits, "limits must not be null");
            return this;
        }

        /**
         * Builds the definition.
         *
         * @return validated immutable search definition
         */
        public SearchDefinition<T> build() {
            SearchPolicy.Builder customizedLimits = limits == null
                    ? customizedLimitsBuilder()
                    : null;
            SearchPolicy effectiveLimits = limits == null
                    ? customizedLimits.build()
                    : limits;
            SearchPolicy definitionLimits = limits != null || limitsCustomizer != null
                    ? effectiveLimits
                    : null;
            return new SearchDefinition<>(
                    fields.entity,
                    fields.build(effectiveLimits.paths()),
                    paging == null ? SearchPaging.disabled() : paging.build(),
                    query == null ? SearchQuery.disabled() : query,
                    definitionLimits,
                    limitsCustomizer == null ? null : customizedLimits.buildOverlay());
        }

        private void ensureQueryIsNotDeclared() {
            if (query != null) {
                throw new IllegalArgumentException("query is already declared");
            }
        }

        private void ensureLimitsAreNotDeclared() {
            if (limits != null || limitsCustomizer != null) {
                throw new IllegalArgumentException("limits are already declared");
            }
        }

        private SearchPolicy.Builder customizedLimitsBuilder() {
            SearchPolicy.Builder builder = baseLimits.toBuilder();
            if (limitsCustomizer != null) {
                limitsCustomizer.accept(builder);
            }
            return builder;
        }
    }

    /**
     * DSL for declaring unique public selectors.
     *
     * @param <T> root entity type
     */
    public static final class Fields<T> {
        private final Class<T> entity;
        private final Map<String, SearchField.Builder<?>> fieldBuilders = new LinkedHashMap<>();

        private Fields(Class<T> entity) {
            this.entity = entity;
        }

        /**
         * Starts a field declaration.
         *
         * @param selector unique public selector
         * @param type exposed value type
         * @param <V> value type
         * @return field builder
         */
        public <V> SearchField.Builder<V> add(String selector, Class<V> type) {
            Assert.hasText(selector, "selector must not be blank");
            Assert.notNull(type, "type must not be null");

            SearchField.Builder<V> field = SearchField.builder(entity, selector, type);
            if (fieldBuilders.putIfAbsent(selector, field) != null) {
                throw new IllegalArgumentException("selector '%s' is already declared".formatted(selector));
            }
            return field;
        }

        /**
         * Declares and customizes a field in one call.
         *
         * @param selector unique public selector
         * @param type exposed value type
         * @param customizer field customizer
         * @param <V> value type
         * @return this fields DSL
         */
        public <V> Fields<T> add(
                String selector, Class<V> type, Consumer<SearchField.Builder<V>> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(add(selector, type));
            return this;
        }

        private Map<String, SearchField<?>> build(SearchPolicy.Paths pathLimits) {
            Map<String, SearchField<?>> built = new LinkedHashMap<>();
            fieldBuilders.forEach((selector, field) -> built.put(selector, field.build(pathLimits)));
            return built;
        }
    }
}
