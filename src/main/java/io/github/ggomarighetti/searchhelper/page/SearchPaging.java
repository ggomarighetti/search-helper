package io.github.ggomarighetti.searchhelper.page;

import io.github.ggomarighetti.searchhelper.validation.HibernateRuleValidator;
import io.github.ggomarighetti.searchhelper.validation.RuleViolation;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import java.util.Objects;
import org.hibernate.validator.cfg.ConstraintDef;

/** Definition-level Bean Validation rules for page number and page size. */
public final class SearchPaging {
    private static final SearchPaging DISABLED =
            new SearchPaging(false, HibernateRuleValidator.none(), HibernateRuleValidator.none());

    private final boolean enabled;
    private final HibernateRuleValidator<Integer> page;
    private final HibernateRuleValidator<Integer> size;

    private SearchPaging(
            boolean enabled,
            HibernateRuleValidator<Integer> page,
            HibernateRuleValidator<Integer> size) {
        this.enabled = enabled;
        this.page = Objects.requireNonNull(page, "page must not be null");
        this.size = Objects.requireNonNull(size, "size must not be null");
    }

    /**
     * Returns disabled paging.
     *
     * @return reusable disabled paging declaration
     */
    public static SearchPaging disabled() {
        return DISABLED;
    }

    /**
     * Creates a paging builder.
     *
     * @return new paging builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reports whether paging is enabled.
     *
     * @return {@code true} when paging was declared
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Tests page and size together.
     *
     * @param page zero-based page number
     * @param size requested page size
     * @return {@code true} when paging is enabled and both values are valid
     */
    public boolean accepts(int page, int size) {
        return enabled && this.page.accepts(page) && this.size.accepts(size);
    }

    /**
     * Tests a page number.
     *
     * @param page zero-based page number
     * @return {@code true} when paging is enabled and the value is valid
     */
    public boolean acceptsPage(int page) {
        return enabled && this.page.accepts(page);
    }

    /**
     * Tests a page size.
     *
     * @param size requested page size
     * @return {@code true} when paging is enabled and the value is valid
     */
    public boolean acceptsSize(int size) {
        return enabled && this.size.accepts(size);
    }

    /**
     * Returns page-number violations.
     *
     * @param page zero-based page number
     * @return immutable safe violations
     */
    public List<RuleViolation> pageViolations(int page) {
        return enabled ? this.page.violations(page) : List.of();
    }

    /**
     * Returns page-size violations.
     *
     * @param size requested page size
     * @return immutable safe violations
     */
    public List<RuleViolation> sizeViolations(int size) {
        return enabled ? this.size.violations(size) : List.of();
    }

    /** Builder for definition-level paging rules. */
    public static final class Builder {
        private final Rules<Integer> page = new Rules<>();
        private final Rules<Integer> size = new Rules<>();

        private Builder() {
        }

        /**
         * Adds page-number constraints.
         *
         * @param customizer page rule customizer
         * @return this builder
         */
        public Builder page(Consumer<Rules<Integer>> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            customizer.accept(page);
            return this;
        }

        /**
         * Adds page-size constraints.
         *
         * @param customizer size rule customizer
         * @return this builder
         */
        public Builder size(Consumer<Rules<Integer>> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            customizer.accept(size);
            return this;
        }

        /**
         * Builds paging rules.
         *
         * @return enabled immutable paging declaration
         */
        public SearchPaging build() {
            return new SearchPaging(
                    true,
                    HibernateRuleValidator.forType(Integer.class, page.rules()),
                    HibernateRuleValidator.forType(Integer.class, size.rules()));
        }
    }

    /**
     * Programmatic Hibernate Validator constraints for one paging value.
     *
     * @param <T> validated value type
     */
    public static final class Rules<T> {
        private final List<ConstraintDef<?, ?>> constraints = new ArrayList<>();

        /** Creates an empty rule declaration. */
        public Rules() {
            // Intentionally empty: callers add optional constraints through rule(...).
        }

        /**
         * Adds a constraint.
         *
         * @param rule Hibernate Validator constraint definition
         * @return this declaration
         */
        public Rules<T> rule(ConstraintDef<?, ?> rule) {
            constraints.add(Objects.requireNonNull(rule, "rule must not be null"));
            return this;
        }

        private List<ConstraintDef<?, ?>> rules() {
            return List.copyOf(constraints);
        }
    }
}
