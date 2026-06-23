package io.github.ggomarighetti.jparsqlsearch.sort;

import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.Assert;

/** Per-selector sorting capabilities and resolved JPA path metadata. */
public final class SearchSorting {
    private static final SearchSorting DISABLED =
            new SearchSorting(false, null, Set.of(), false, Set.of(), SearchPath.Topology.none());

    private final boolean enabled;
    private final String path;
    private final Set<Direction> directions;
    private final boolean ignoreCase;
    private final Set<Sort.NullHandling> nullHandling;
    private final SearchPath.Topology topology;

    private SearchSorting(
            boolean enabled,
            String path,
            Set<Direction> directions,
            boolean ignoreCase,
            Set<Sort.NullHandling> nullHandling,
            SearchPath.Topology topology) {
        this.enabled = enabled;
        this.path = path;
        this.directions = directions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(directions));
        this.ignoreCase = ignoreCase;
        this.nullHandling = nullHandling.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(nullHandling));
        this.topology = Objects.requireNonNull(topology, "topology must not be null");
    }

    /**
     * Returns a disabled sorting definition.
     *
     * @return shared disabled definition
     */
    public static SearchSorting disabled() {
        return DISABLED;
    }

    /**
     * Creates a sorting definition builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reports whether sorting is enabled.
     *
     * @return enabled state
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the resolved entity path.
     *
     * @return entity path, or {@code null} when disabled
     */
    public String path() {
        return path;
    }

    /**
     * Returns accepted sort directions.
     *
     * @return immutable direction set
     */
    public Set<Direction> directions() {
        return directions;
    }

    /**
     * Reports whether case-insensitive sorting is allowed.
     *
     * @return allowed state
     */
    public boolean ignoreCase() {
        return ignoreCase;
    }

    /**
     * Returns accepted null-handling modes.
     *
     * @return immutable mode set
     */
    public Set<Sort.NullHandling> nullHandling() {
        return nullHandling;
    }

    /**
     * Returns topology information for the resolved path.
     *
     * @return path topology
     */
    public SearchPath.Topology topology() {
        return topology;
    }

    /**
     * Tests whether a direction is accepted.
     *
     * @param direction requested direction
     * @return acceptance result
     */
    public boolean accepts(Direction direction) {
        Objects.requireNonNull(direction, "direction must not be null");
        return enabled && directions.contains(direction);
    }

    /**
     * Tests whether the requested case mode is accepted.
     *
     * @param ignoreCase requested case-insensitive state
     * @return acceptance result
     */
    public boolean acceptsIgnoreCase(boolean ignoreCase) {
        return enabled && (!ignoreCase || this.ignoreCase);
    }

    /**
     * Tests whether a null-handling mode is accepted.
     *
     * @param nullHandling requested mode
     * @return acceptance result
     */
    public boolean acceptsNullHandling(Sort.NullHandling nullHandling) {
        Objects.requireNonNull(nullHandling, "nullHandling must not be null");
        return enabled && this.nullHandling.contains(nullHandling);
    }

    /** Builder for a selector's sorting capabilities. */
    public static final class Builder {
        private String path;
        private final EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        private boolean ignoreCase;
        private final EnumSet<Sort.NullHandling> nullHandling =
                EnumSet.of(Sort.NullHandling.NATIVE);

        private Builder() {
        }

        /**
         * Overrides the entity path used for sorting.
         *
         * @param path non-blank entity path
         * @return this builder
         */
        public Builder path(String path) {
            Assert.hasText(path, "path must not be blank");
            this.path = path;
            return this;
        }

        /**
         * Restricts accepted directions.
         *
         * @param directions accepted directions
         * @return this builder
         */
        public Builder allow(Direction... directions) {
            Assert.notEmpty(directions, "directions must not be empty");
            for (Direction direction : directions) {
                this.directions.add(Objects.requireNonNull(direction, "directions must not contain null values"));
            }
            return this;
        }

        /**
         * Enables case-insensitive sorting.
         *
         * @return this builder
         */
        public Builder allowIgnoreCase() {
            ignoreCase = true;
            return this;
        }

        /**
         * Adds accepted null-handling modes.
         *
         * @param nullHandling accepted modes
         * @return this builder
         */
        public Builder allowNullHandling(Sort.NullHandling... nullHandling) {
            Assert.notEmpty(nullHandling, "nullHandling must not be empty");
            for (Sort.NullHandling value : nullHandling) {
                this.nullHandling.add(Objects.requireNonNull(value, "nullHandling must not contain null values"));
            }
            return this;
        }

        /**
         * Resolves and validates sorting metadata for a selector.
         *
         * @param entity entity type
         * @param selector external selector
         * @param type declared selector type
         * @param defaultPath default entity path
         * @param pathLimits path policy
         * @return validated sorting definition
         */
        public SearchSorting build(
                Class<?> entity,
                String selector,
                Class<?> type,
                String defaultPath,
                SearchPolicy.Paths pathLimits) {
            String resolvedPath = path == null ? defaultPath : path;
            SearchPath.Metadata pathMetadata = SearchPath.metadata(entity, selector, resolvedPath, type, pathLimits);
            SearchPath.Topology topology = SearchPath.topology(entity, selector, resolvedPath, pathLimits);
            if (pathMetadata.traversesCollection() || pathMetadata.collectionValued()) {
                throw new IllegalArgumentException(
                        "selector '%s' sorting path '%s' must not traverse collection-valued paths"
                                .formatted(selector, resolvedPath));
            }
            if (ignoreCase && !CharSequence.class.isAssignableFrom(pathMetadata.type())) {
                throw new IllegalArgumentException(
                        "selector '%s' ignoreCase sorting requires a CharSequence path".formatted(selector));
            }
            Set<Direction> resolvedDirections =
                    directions.isEmpty() ? EnumSet.allOf(Direction.class) : EnumSet.copyOf(directions);
            return new SearchSorting(
                    true,
                    resolvedPath,
                    resolvedDirections,
                    ignoreCase,
                    nullHandling,
                    topology);
        }
    }
}
