package io.github.ggomarighetti.jparsqlsearch.path;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

record ResolvedSegment(
        Class<?> type,
        Type genericType,
        AnnotatedElement primary,
        AnnotatedElement secondary,
        Map<TypeVariable<?>, Type> typeVariables) {
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
