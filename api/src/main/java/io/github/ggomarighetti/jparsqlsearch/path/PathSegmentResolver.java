package io.github.ggomarighetti.jparsqlsearch.path;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.springframework.beans.BeanUtils;

final class PathSegmentResolver {
    private PathSegmentResolver() {
    }

    static ResolvedSegment resolve(Class<?> owner, String segment) {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(owner, segment);
        Field field = findField(owner, segment);
        if (descriptor != null && descriptor.getReadMethod() != null) {
            Method readMethod = descriptor.getReadMethod();
            return new ResolvedSegment(
                    readMethod.getReturnType(),
                    readMethod.getGenericReturnType(),
                    readMethod,
                    field,
                    GenericTypeResolver.typeVariables(owner, readMethod.getDeclaringClass()));
        }
        Class<?> descriptorType = descriptor == null ? null : descriptor.getPropertyType();
        if (descriptorType != null) {
            return new ResolvedSegment(
                    descriptorType,
                    descriptorType,
                    null,
                    field,
                    Map.of());
        }
        return field == null
                ? null
                : new ResolvedSegment(
                        field.getType(),
                        field.getGenericType(),
                        field,
                        null,
                        GenericTypeResolver.typeVariables(owner, field.getDeclaringClass()));
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
}
