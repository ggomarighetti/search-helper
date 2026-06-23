package io.github.ggomarighetti.jparsqlsearch.path;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

record ResolvedWildcardType(List<Type> upperBounds, List<Type> lowerBounds) implements WildcardType {
    ResolvedWildcardType(Type[] upperBounds, Type[] lowerBounds) {
        this(List.of(upperBounds), List.of(lowerBounds));
    }

    @Override
    public Type[] getUpperBounds() {
        return upperBounds.toArray(Type[]::new);
    }

    @Override
    public Type[] getLowerBounds() {
        return lowerBounds.toArray(Type[]::new);
    }
}
