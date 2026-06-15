package io.github.ggomarighetti.searchhelper.definition;

import io.github.ggomarighetti.searchhelper.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.beans.PropertyDescriptor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanUtils;
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
        ResolvedPath resolved = resolve(entity, selector, path, limits);
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
        ResolvedPath resolved = resolve(entity, selector, path, limits);
        return new Topology(
                resolved.depth(),
                resolved.joinedPaths(),
                resolved.toManyPaths());
    }

    private static ResolvedPath resolve(Class<?> entity, String selector, String path, SearchPolicy.Paths limits) {
        String[] segments = path.split("\\.");
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
            recordTopology(
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
        ResolvedSegment resolved = resolveSegment(current, segment);
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

    private static void recordTopology(
            String resolvedPath,
            ResolvedSegment resolved,
            Class<?> resolvedType,
            boolean collectionValued,
            Set<String> joinedPaths,
            Set<String> toManyPaths) {
        boolean toMany = isToMany(resolved, collectionValued);
        if (isAssociation(resolved, resolvedType, toMany)) {
            joinedPaths.add(resolvedPath);
        }
        if (toMany) {
            toManyPaths.add(resolvedPath);
        }
    }

    private static boolean isToMany(ResolvedSegment resolved, boolean collectionValued) {
        return collectionValued || resolved.hasAnyAnnotation(
                OneToMany.class,
                ManyToMany.class,
                ElementCollection.class);
    }

    private static boolean isAssociation(ResolvedSegment resolved, Class<?> resolvedType, boolean toMany) {
        return toMany
                || resolved.hasAnyAnnotation(ManyToOne.class, OneToOne.class)
                || resolvedType.isAnnotationPresent(Entity.class);
    }

    private static Class<?> collectionElementTypeOrThrow(
            ResolvedSegment resolved,
            Class<?> entity,
            String selector,
            String path) {
        Class<?> elementType = collectionElementType(resolved.type(), resolved.genericType());
        if (elementType == null) {
            throw unresolved(entity, selector, path);
        }
        return elementType;
    }

    private static ResolvedSegment resolveSegment(Class<?> owner, String segment) {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(owner, segment);
        Field field = findField(owner, segment);
        if (descriptor != null && descriptor.getReadMethod() != null) {
            Method readMethod = descriptor.getReadMethod();
            return new ResolvedSegment(
                    readMethod.getReturnType(),
                    readMethod.getGenericReturnType(),
                    readMethod,
                    field);
        }
        if (descriptor != null && descriptor.getPropertyType() != null) {
            return new ResolvedSegment(
                    descriptor.getPropertyType(),
                    descriptor.getPropertyType(),
                    null,
                    field);
        }
        return field == null
                ? null
                : new ResolvedSegment(field.getType(), field.getGenericType(), field, null);
    }

    private static Class<?> collectionElementType(Class<?> type, Type genericType) {
        if (type.isArray()) {
            return type.getComponentType();
        }
        if (genericType instanceof GenericArrayType genericArrayType) {
            return classFromType(genericArrayType.getGenericComponentType());
        }
        if (Map.class.isAssignableFrom(type)) {
            return genericArgument(genericType, Map.class, 1);
        }
        if (Iterable.class.isAssignableFrom(type)) {
            return genericArgument(genericType, Iterable.class, 0);
        }
        return null;
    }

    private static Class<?> genericArgument(Type type, Class<?> target, int index) {
        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawClass = rawClass(parameterizedType.getRawType());
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (rawClass != null && target.isAssignableFrom(rawClass) && arguments.length > index) {
                return classFromType(arguments[index]);
            }
            Class<?> resolved = genericArgument(rawClass, target, index);
            if (resolved != null) {
                return resolved;
            }
        }
        return genericArgument(rawClass(type), target, index);
    }

    private static Class<?> genericArgument(Class<?> type, Class<?> target, int index) {
        if (type == null || Object.class.equals(type)) {
            return null;
        }
        for (Type interfaceType : type.getGenericInterfaces()) {
            Class<?> resolved = genericArgument(interfaceType, target, index);
            if (resolved != null) {
                return resolved;
            }
        }
        return genericArgument(type.getGenericSuperclass(), target, index);
    }

    private static Class<?> classFromType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return rawClass(parameterizedType.getRawType());
        }
        if (type instanceof GenericArrayType genericArrayType) {
            Class<?> componentType = classFromType(genericArrayType.getGenericComponentType());
            return componentType == null ? null : Array.newInstance(componentType, 0).getClass();
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            return upperBounds.length == 0 ? null : classFromType(upperBounds[0]);
        }
        if (type instanceof TypeVariable<?>) {
            return null;
        }
        return null;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return rawClass(parameterizedType.getRawType());
        }
        return null;
    }

    private static boolean isCollectionValued(Class<?> type) {
        return type.isArray()
                || Iterable.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type);
    }

    private record ResolvedSegment(
            Class<?> type,
            Type genericType,
            AnnotatedElement primary,
            AnnotatedElement secondary) {
        @SafeVarargs
        final boolean hasAnyAnnotation(Class<? extends java.lang.annotation.Annotation>... annotations) {
            for (Class<? extends java.lang.annotation.Annotation> annotation : annotations) {
                if ((primary != null && primary.isAnnotationPresent(annotation))
                        || (secondary != null && secondary.isAnnotationPresent(annotation))) {
                    return true;
                }
            }
            return false;
        }
    }

    private record ResolvedPath(
            Class<?> type,
            boolean traversesCollection,
            boolean collectionValued,
            int depth,
            Set<String> joinedPaths,
            Set<String> toManyPaths) {
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

    private static Field findField(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null && !Object.class.equals(current)) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static IllegalArgumentException unresolved(Class<?> entity, String selector, String path) {
        return new IllegalArgumentException(
                "selector '%s' path '%s' could not be resolved against entity '%s'"
                        .formatted(selector, path, entity.getName()));
    }
}
