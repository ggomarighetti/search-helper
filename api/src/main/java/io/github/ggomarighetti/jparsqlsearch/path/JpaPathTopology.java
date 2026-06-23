package io.github.ggomarighetti.jparsqlsearch.path;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.Set;

final class JpaPathTopology {
    private JpaPathTopology() {
    }

    static void register(
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
}
