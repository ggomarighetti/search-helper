package io.github.ggomarighetti.jparsqlsearch.query;

import io.github.ggomarighetti.jparsqlsearch.validation.HibernateRuleValidator;
import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.validator.cfg.ConstraintDef;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable free-text query policy.
 *
 * @param <T> entity type targeted by the generated specification
 */
public final class SearchQuery<T> implements AutoCloseable {
    private static final SearchQuery<?> DISABLED =
            new SearchQuery<>(false, HibernateRuleValidator.none(), false, null);

    private final boolean enabled;
    private final HibernateRuleValidator<String> validator;
    private final boolean hasRules;
    private final SearchQuerySpecification<T> specification;

    private SearchQuery(
            boolean enabled,
            HibernateRuleValidator<String> validator,
            boolean hasRules,
            SearchQuerySpecification<T> specification) {
        this.enabled = enabled;
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.hasRules = hasRules;
        this.specification = specification;
    }

    /**
     * Returns a disabled query definition.
     *
     * @param <T> entity type
     * @return shared disabled definition
     */
    @SuppressWarnings("unchecked")
    public static <T> SearchQuery<T> disabled() {
        return (SearchQuery<T>) DISABLED;
    }

    /**
     * Creates an enabled query definition builder.
     *
     * @param <T> entity type
     * @return new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Reports whether free-text querying is enabled.
     *
     * @return enabled state
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Reports whether explicit validation rules were configured.
     *
     * @return {@code true} when at least one rule exists
     */
    public boolean hasRules() {
        return hasRules;
    }

    /**
     * Tests query text against the configured rules.
     *
     * @param query query text
     * @return whether querying is enabled and all rules accept the text
     */
    public boolean accepts(String query) {
        return enabled && validator.accepts(query);
    }

    /**
     * Returns validation violations for query text.
     *
     * @param query query text
     * @return immutable violation list, or an empty list when disabled
     */
    public List<RuleViolation> violations(String query) {
        return enabled ? validator.violations(query) : List.of();
    }

    /**
     * Builds the configured JPA specification.
     *
     * @param query validated query text
     * @return query specification
     * @throws IllegalStateException when querying is disabled
     */
    public Specification<T> toSpecification(String query) {
        if (!enabled) {
            throw new IllegalStateException("query is disabled");
        }
        return specification.toSpecification(query);
    }

    /** Releases validator resources owned by this query declaration. */
    @Override
    public void close() {
        validator.close();
    }

    /**
     * Builder for an enabled query definition.
     *
     * @param <T> entity type
     */
    public static final class Builder<T> {
        private final List<ConstraintDef<?, ?>> rules = new ArrayList<>();
        private SearchQuerySpecification<T> specification;

        private Builder() {
        }

        /**
         * Adds a Hibernate Validator programmatic constraint.
         *
         * @param rule constraint definition
         * @return this builder
         */
        public Builder<T> rule(ConstraintDef<?, ?> rule) {
            rules.add(Objects.requireNonNull(rule, "rule must not be null"));
            return this;
        }

        /**
         * Sets the specification factory.
         *
         * @param specification factory invoked with validated query text
         * @return this builder
         * @throws IllegalArgumentException when a factory was already declared
         */
        public Builder<T> specification(SearchQuerySpecification<T> specification) {
            if (this.specification != null) {
                throw new IllegalArgumentException("query specification is already declared");
            }
            this.specification = Objects.requireNonNull(specification, "specification must not be null");
            return this;
        }

        /**
         * Builds the query definition.
         *
         * @return enabled query definition
         * @throws IllegalArgumentException when no specification factory was declared
         */
        public SearchQuery<T> build() {
            if (specification == null) {
                throw new IllegalArgumentException("query specification must be declared");
            }
            return new SearchQuery<>(
                    true,
                    HibernateRuleValidator.forType(String.class, List.copyOf(rules)),
                    !rules.isEmpty(),
                    specification);
        }
    }
}
