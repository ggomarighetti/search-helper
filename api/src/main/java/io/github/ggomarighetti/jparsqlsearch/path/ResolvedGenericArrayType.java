package io.github.ggomarighetti.jparsqlsearch.path;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

record ResolvedGenericArrayType(Type genericComponentType) implements GenericArrayType {
    @Override
    public Type getGenericComponentType() {
        return genericComponentType;
    }
}
