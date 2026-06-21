package io.github.ggomarighetti.jparsqlsearch.path;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.Set;
import org.springframework.util.ClassUtils;

/** Utilities for validating Java/JPA paths and deriving their join topology. */
public final class SearchPath {
    private SearchPath() {
    }

    /**
     * Validates a path with default depth limits.
     *
     * @param entity root entity type
     * @param selector public selector used in error messages
     * @param path dot-separated Java/JPA path
     * @param type expected terminal type
     */
    public static void validate(Class<?> entity, String selector, String path, Class<?> type) {
        validate(entity, selector, path, type, SearchPolicy.defaults().paths());
    }

    /**
     * Validates a path with explicit depth limits.
     *
     * @param entity root entity type
     * @param selector public selector used in error messages
     * @param path dot-separated Java/JPA path
     * @param type expected terminal type
     * @param limits path depth limits
     */
    public static void validate(
            Class<?> entity,
            String selector,
            String path,
            Class<?> type,
            SearchPolicy.Paths limits) {
        metadata(entity, selector, path, type, limits);
    }

    /**
     * Resolves terminal type and collection metadata.
     *
     * @param entity root entity type
     * @param selector public selector used in error messages
     * @param path dot-separated Java/JPA path
     * @param type expected terminal type
     * @param limits path depth limits
     * @return resolved path metadata
     */
    public static Metadata metadata(
            Class<?> entity,
            String selector,
            String path,
            Class<?> type,
            SearchPolicy.Paths limits) {
        ResolvedPath resolved = PathResolver.resolve(entity, selector, path, limits);
        Class<?> pathType = ClassUtils.resolvePrimitiveIfNecessary(resolved.type());
        Class<?> fieldType = ClassUtils.resolvePrimitiveIfNecessary(type);
        if (!pathType.equals(fieldType)) {
            throw new IllegalArgumentException(
                    "selector '%s' path '%s' resolves to type '%s' but was declared as '%s'"
                            .formatted(selector, path, resolved.type().getName(), type.getName()));
        }
        return new Metadata(resolved.type(), resolved.traversesCollection(), resolved.collectionValued());
    }

    /**
     * Resolves join and to-many topology.
     *
     * @param entity root entity type
     * @param selector public selector used in error messages
     * @param path dot-separated Java/JPA path
     * @param limits path depth limits
     * @return immutable path topology
     */
    public static Topology topology(
            Class<?> entity,
            String selector,
            String path,
            SearchPolicy.Paths limits) {
        ResolvedPath resolved = PathResolver.resolve(entity, selector, path, limits);
        return new Topology(
                resolved.depth(),
                resolved.joinedPaths(),
                resolved.toManyPaths());
    }

    /**
     * Splits a dot-separated path while preserving empty leading, middle, and trailing segments.
     *
     * @param path dot-separated Java/JPA path
     * @return path segments including malformed empty segments
     */
    public static String[] segments(String path) {
        return PathSegments.split(path);
    }

    /**
     * Terminal type and collection characteristics of a path.
     *
     * @param type terminal Java type
     * @param traversesCollection whether an intermediate segment is collection-valued
     * @param collectionValued whether the terminal segment is collection-valued
     */
    public record Metadata(Class<?> type, boolean traversesCollection, boolean collectionValued) {
    }

    /**
     * Join topology derived from a path.
     *
     * @param depth number of path segments
     * @param joinedPaths immutable relation path prefixes
     * @param toManyPaths immutable to-many path prefixes
     */
    public record Topology(int depth, Set<String> joinedPaths, Set<String> toManyPaths) {
        /** Copies topology sets into immutable values. */
        public Topology {
            joinedPaths = Set.copyOf(joinedPaths);
            toManyPaths = Set.copyOf(toManyPaths);
        }

        /**
         * Returns empty topology.
         *
         * @return topology for a scalar root property
         */
        public static Topology none() {
            return new Topology(0, Set.of(), Set.of());
        }
    }
}
