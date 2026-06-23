package io.github.ggomarighetti.jparsqlsearch.path;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

record ResolvedParameterizedType(Type ownerType, Type rawType, List<Type> actualTypeArguments)
        implements ParameterizedType {
    ResolvedParameterizedType(Type ownerType, Type rawType, Type[] actualTypeArguments) {
        this(ownerType, rawType, List.of(actualTypeArguments));
    }

    @Override
    public Type[] getActualTypeArguments() {
        return actualTypeArguments.toArray(Type[]::new);
    }

    @Override
    public Type getRawType() {
        return rawType;
    }

    @Override
    public Type getOwnerType() {
        return ownerType;
    }
}
