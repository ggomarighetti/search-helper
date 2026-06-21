package io.github.ggomarighetti.jparsqlsearch.path;

import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class PathResolver {
    private PathResolver() {
    }

    static ResolvedPath resolve(Class<?> entity, String selector, String path, SearchPolicy.Paths limits) {
        String[] segments = PathSegments.split(path);
        validateDepth(selector, path, limits, segments.length);
        Class<?> current = entity;
        boolean traversesCollection = false;
        boolean collectionValued = false;
        Set<String> joinedPaths = new LinkedHashSet<>();
        Set<String> toManyPaths = new LinkedHashSet<>();
        StringBuilder resolvedPrefix = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            ResolvedSegment resolved = resolveRequiredSegment(current, segment, entity, selector, path);
            Class<?> resolvedType = resolved.type();
            collectionValued = isCollectionValued(resolvedType);
            appendPrefix(resolvedPrefix, segment);
            JpaPathTopology.register(
                    resolvedPrefix.toString(),
                    resolved,
                    resolvedType,
                    collectionValued,
                    joinedPaths,
                    toManyPaths);
            if (collectionValued && index < segments.length - 1) {
                Class<?> elementType = collectionElementTypeOrThrow(resolved, entity, selector, path);
                traversesCollection = true;
                current = elementType;
            } else {
                current = resolvedType;
            }
        }
        return new ResolvedPath(
                current,
                traversesCollection,
                collectionValued,
                segments.length,
                Collections.unmodifiableSet(joinedPaths),
                Collections.unmodifiableSet(toManyPaths));
    }

    private static void validateDepth(String selector, String path, SearchPolicy.Paths limits, int depth) {
        if (depth > limits.maxDepth()) {
            throw new SearchDefinitionValidationException(
                    SearchDefinitionValidationException.PATH_LIMIT_EXCEEDED,
                    "selector '%s' path '%s' exceeds maximum path depth of %d"
                            .formatted(selector, path, limits.maxDepth()));
        }
    }

    private static ResolvedSegment resolveRequiredSegment(
            Class<?> current,
            String segment,
            Class<?> entity,
            String selector,
            String path) {
        if (segment.isBlank()) {
            throw unresolved(entity, selector, path);
        }
        ResolvedSegment resolved = PathSegmentResolver.resolve(current, segment);
        if (resolved == null) {
            throw unresolved(entity, selector, path);
        }
        return resolved;
    }

    private static void appendPrefix(StringBuilder resolvedPrefix, String segment) {
        if (!resolvedPrefix.isEmpty()) {
            resolvedPrefix.append('.');
        }
        resolvedPrefix.append(segment);
    }

    private static Class<?> collectionElementTypeOrThrow(
            ResolvedSegment resolved,
            Class<?> entity,
            String selector,
            String path) {
        Class<?> elementType = GenericTypeResolver.collectionElementType(
                resolved.type(),
                resolved.genericType(),
                resolved.typeVariables());
        if (elementType == null) {
            throw unresolved(entity, selector, path);
        }
        return elementType;
    }

    private static boolean isCollectionValued(Class<?> type) {
        return type.isArray()
                || Iterable.class.isAssignableFrom(type)
                || java.util.Map.class.isAssignableFrom(type);
    }

    private static IllegalArgumentException unresolved(Class<?> entity, String selector, String path) {
        return new IllegalArgumentException(
                "selector '%s' path '%s' could not be resolved against entity '%s'"
                        .formatted(selector, path, entity.getName()));
    }
}
