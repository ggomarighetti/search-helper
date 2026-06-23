package io.github.ggomarighetti.jparsqlsearch.definition;

import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.filter.SearchFiltering;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import java.util.function.Consumer;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.Assert;

/**
 * One public selector and its filtering and sorting capabilities.
 *
 * @param <T> exposed value type
 */
public final class SearchField<T> implements AutoCloseable {
    private final String selector;
    private final Optional<String> path;
    private final Class<T> type;
    private final Optional<Class<?>> subtype;
    private final SearchFiltering<T> filtering;
    private final SearchSorting sorting;

    private SearchField(
            String selector,
            String path,
            Class<T> type,
            Class<?> subtype,
            SearchFiltering<T> filtering,
            SearchSorting sorting) {
        Assert.hasText(selector, "selector must not be blank");
        Assert.notNull(type, "type must not be null");
        this.selector = selector;
        this.path = Optional.ofNullable(path);
        this.type = type;
        this.subtype = Optional.ofNullable(subtype);
        this.filtering = Objects.requireNonNull(filtering, "filtering must not be null");
        this.sorting = Objects.requireNonNull(sorting, "sorting must not be null");
    }

    static <T> Builder<T> builder(Class<?> entity, String selector, Class<T> type) {
        return new Builder<>(entity, selector, type);
    }

    /**
     * Returns the public selector.
     *
     * @return stable public selector
     */
    public String selector() {
        return selector;
    }

    /**
     * Returns the declared shared path.
     *
     * @return explicitly declared base path, when present
     */
    public Optional<String> path() {
        return path;
    }

    /**
     * Returns the exposed value type.
     *
     * @return exposed value type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Returns the optional path subtype.
     *
     * @return JPA subtype owning the path, when configured
     */
    public Optional<Class<?>> subtype() {
        return subtype;
    }

    /**
     * Returns filtering capabilities.
     *
     * @return filtering capability declaration
     */
    public SearchFiltering<T> filtering() {
        return filtering;
    }

    /**
     * Returns sorting capabilities.
     *
     * @return sorting capability declaration
     */
    public SearchSorting sorting() {
        return sorting;
    }

    /** Releases validator resources owned by this field. */
    @Override
    public void close() {
        filtering.close();
    }

    /**
     * Builder for one search field.
     *
     * @param <T> exposed value type
     */
    public static final class Builder<T> {
        private final Class<?> entity;
        private final String selector;
        private final Class<T> type;
        private String path;
        private Class<?> subtype;
        private SearchFiltering.Builder<T> filtering;
        private SearchSorting.Builder sorting;

        private Builder(Class<?> entity, String selector, Class<T> type) {
            this.entity = Objects.requireNonNull(entity, "entity must not be null");
            Assert.hasText(selector, "selector must not be blank");
            Assert.notNull(type, "type must not be null");
            this.selector = selector;
            this.type = type;
        }

        /**
         * Configures the shared default JPA path.
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
         * Resolves this field's effective paths against a concrete JPA entity subtype.
         *
         * @param subtype managed subtype owning the field path
         * @return this builder
         */
        public Builder<T> subtype(Class<?> subtype) {
            Objects.requireNonNull(subtype, "subtype must not be null");
            if (!entity.isAssignableFrom(subtype)) {
                throw new IllegalArgumentException(
                        "subtype '%s' must extend entity '%s'"
                                .formatted(subtype.getName(), entity.getName()));
            }
            this.subtype = subtype;
            return this;
        }

        /**
         * Enables filtering with an explicit operator whitelist.
         *
         * <p>Default operators are not included unless the customizer calls
         * {@link SearchFiltering.Builder#withDefaults()}.
         *
         * @param customizer filtering customizer
         * @return this builder
         */
        public Builder<T> filterable(Consumer<SearchFiltering.Builder<T>> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            if (filtering != null) {
                throw new IllegalArgumentException("selector '%s' already declares filtering".formatted(selector));
            }
            filtering = SearchFiltering.builder();
            customizer.accept(filtering);
            return this;
        }

        /**
         * Enables filtering with the restrictive default profile for this field type.
         *
         * @return this builder
         */
        public Builder<T> filterable() {
            return filterable(SearchFiltering.Builder::withDefaults);
        }

        /**
         * Enables sorting in both directions on the effective field path.
         *
         * @return this builder
         */
        public Builder<T> sortable() {
            return sortable(ignored -> {});
        }

        /**
         * Enables and customizes sorting.
         *
         * @param customizer sorting customizer
         * @return this builder
         */
        public Builder<T> sortable(Consumer<SearchSorting.Builder> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            if (sorting != null) {
                throw new IllegalArgumentException("selector '%s' already declares sorting".formatted(selector));
            }
            sorting = SearchSorting.builder();
            customizer.accept(sorting);
            return this;
        }

        /**
         * Enables filtering with the default profile and sorting in both directions.
         *
         * @return this builder
         */
        public Builder<T> searchable() {
            return filterable().sortable();
        }

        SearchField<T> build(SearchPolicy.Paths pathLimits) {
            Objects.requireNonNull(pathLimits, "pathLimits must not be null");
            Class<?> pathRoot = subtype == null ? entity : subtype;
            String defaultPath = path == null ? selector : path;
            if (path != null) {
                SearchPath.validate(pathRoot, selector, path, type, pathLimits);
            }
            return new SearchField<>(
                    selector,
                    path,
                    type,
                    subtype,
                    filtering == null
                            ? SearchFiltering.disabled()
                            : filtering.build(pathRoot, selector, type, defaultPath, pathLimits),
                    sorting == null
                            ? SearchSorting.disabled()
                            : sorting.build(pathRoot, selector, type, defaultPath, pathLimits));
        }
    }
}
