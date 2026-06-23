package io.github.ggomarighetti.jparsqlsearch.path;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class GenericTypeResolver {
    private GenericTypeResolver() {
    }

    static Class<?> collectionElementType(
            Class<?> type,
            Type genericType,
            Map<TypeVariable<?>, Type> typeVariables) {
        if (type.isArray()) {
            return type.getComponentType();
        }
        if (genericType instanceof GenericArrayType genericArrayType) {
            return classFromType(genericArrayType.getGenericComponentType(), typeVariables);
        }
        if (Map.class.isAssignableFrom(type)) {
            return genericArgument(genericType, Map.class, 1, typeVariables);
        }
        if (Iterable.class.isAssignableFrom(type)) {
            return genericArgument(genericType, Iterable.class, 0, typeVariables);
        }
        return null;
    }

    static Map<TypeVariable<?>, Type> typeVariables(Class<?> owner, Class<?> declaringClass) {
        if (owner == null || declaringClass == null) {
            return Map.of();
        }
        Map<TypeVariable<?>, Type> mappings = new LinkedHashMap<>();
        if (!resolveTypeVariables(owner, declaringClass, mappings)) {
            return Map.of();
        }
        return Map.copyOf(mappings);
    }

    private static Class<?> genericArgument(
            Type type,
            Class<?> target,
            int index,
            Map<TypeVariable<?>, Type> typeVariables) {
        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawClass = rawClass(parameterizedType.getRawType());
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (rawClass != null && target.isAssignableFrom(rawClass) && arguments.length > index) {
                return classFromType(arguments[index], typeVariables);
            }
            Class<?> resolved = genericArgument(rawClass, target, index, typeVariables);
            if (resolved != null) {
                return resolved;
            }
        }
        return genericArgument(rawClass(type), target, index, typeVariables);
    }

    private static Class<?> genericArgument(
            Class<?> type,
            Class<?> target,
            int index,
            Map<TypeVariable<?>, Type> typeVariables) {
        if (type == null || Object.class.equals(type)) {
            return null;
        }
        for (Type interfaceType : type.getGenericInterfaces()) {
            Class<?> resolved = genericArgument(interfaceType, target, index, typeVariables);
            if (resolved != null) {
                return resolved;
            }
        }
        return genericArgument(type.getGenericSuperclass(), target, index, typeVariables);
    }

    private static Class<?> classFromType(Type type, Map<TypeVariable<?>, Type> typeVariables) {
        Type substituted = substituteType(type, typeVariables);
        if (!substituted.equals(type)) {
            return classFromType(substituted, typeVariables);
        }
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return rawClass(parameterizedType.getRawType());
        }
        if (type instanceof GenericArrayType genericArrayType) {
            Class<?> componentType = classFromType(genericArrayType.getGenericComponentType(), typeVariables);
            return componentType == null ? null : Array.newInstance(componentType, 0).getClass();
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            return upperBounds.length == 0 ? null : classFromType(upperBounds[0], typeVariables);
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            Type[] upperBounds = typeVariable.getBounds();
            return upperBounds.length == 0 ? null : classFromType(upperBounds[0], typeVariables);
        }
        return null;
    }

    private static boolean resolveTypeVariables(
            Type currentType,
            Class<?> target,
            Map<TypeVariable<?>, Type> mappings) {
        Class<?> current = rawClass(currentType);
        if (current == null) {
            return false;
        }
        recordTypeVariables(currentType, current, mappings);
        if (current.equals(target)) {
            return true;
        }
        for (Type interfaceType : current.getGenericInterfaces()) {
            Map<TypeVariable<?>, Type> branch = new LinkedHashMap<>(mappings);
            if (resolveTypeVariables(substituteType(interfaceType, branch), target, branch)) {
                mappings.clear();
                mappings.putAll(branch);
                return true;
            }
        }
        Type superclass = current.getGenericSuperclass();
        return superclass != null && resolveTypeVariables(substituteType(superclass, mappings), target, mappings);
    }

    private static void recordTypeVariables(
            Type currentType,
            Class<?> current,
            Map<TypeVariable<?>, Type> mappings) {
        if (currentType instanceof ParameterizedType parameterizedType) {
            TypeVariable<?>[] variables = current.getTypeParameters();
            Type[] arguments = parameterizedType.getActualTypeArguments();
            for (int index = 0; index < variables.length && index < arguments.length; index++) {
                mappings.put(variables[index], substituteType(arguments[index], mappings));
            }
        }
    }

    private static Type substituteType(Type type, Map<TypeVariable<?>, Type> mappings) {
        if (type instanceof TypeVariable<?> variable) {
            return substituteTypeVariable(variable, mappings);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return substituteParameterizedType(parameterizedType, mappings);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return substituteGenericArrayType(genericArrayType, mappings);
        }
        if (type instanceof WildcardType wildcardType) {
            return substituteWildcardType(wildcardType, mappings);
        }
        return type;
    }

    private static Type substituteTypeVariable(TypeVariable<?> variable, Map<TypeVariable<?>, Type> mappings) {
        Type mapped = mappings.get(variable);
        return mapped == null || mapped.equals(variable) ? variable : substituteType(mapped, mappings);
    }

    private static Type substituteParameterizedType(
            ParameterizedType type,
            Map<TypeVariable<?>, Type> mappings) {
        Type[] arguments = type.getActualTypeArguments();
        Type[] substituted = substituteTypes(arguments, mappings);
        if (Arrays.equals(arguments, substituted)) {
            return type;
        }
        return new ResolvedParameterizedType(
                type.getOwnerType(),
                type.getRawType(),
                substituted);
    }

    private static Type substituteGenericArrayType(
            GenericArrayType type,
            Map<TypeVariable<?>, Type> mappings) {
        Type originalComponent = type.getGenericComponentType();
        Type component = substituteType(originalComponent, mappings);
        if (component.equals(originalComponent)) {
            return type;
        }
        if (component instanceof Class<?> componentClass) {
            return Array.newInstance(componentClass, 0).getClass();
        }
        return new ResolvedGenericArrayType(component);
    }

    private static Type substituteWildcardType(WildcardType type, Map<TypeVariable<?>, Type> mappings) {
        Type[] upperBounds = substituteTypes(type.getUpperBounds(), mappings);
        Type[] lowerBounds = substituteTypes(type.getLowerBounds(), mappings);
        if (Arrays.equals(type.getUpperBounds(), upperBounds) && Arrays.equals(type.getLowerBounds(), lowerBounds)) {
            return type;
        }
        return new ResolvedWildcardType(upperBounds, lowerBounds);
    }

    private static Type[] substituteTypes(Type[] types, Map<TypeVariable<?>, Type> mappings) {
        Type[] substituted = types.clone();
        for (int index = 0; index < substituted.length; index++) {
            substituted[index] = substituteType(substituted[index], mappings);
        }
        return substituted;
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
}
