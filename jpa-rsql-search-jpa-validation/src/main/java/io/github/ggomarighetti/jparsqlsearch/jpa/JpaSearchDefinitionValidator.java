package io.github.ggomarighetti.jparsqlsearch.jpa;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.definition.validation.SearchDefinitionValidator;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.Objects;
import org.springframework.util.ClassUtils;

/** Validates declared filtering and sorting paths against the JPA metamodel. */
public final class JpaSearchDefinitionValidator implements SearchDefinitionValidator {
    private final EntityManagerFactory entityManagerFactory;

    /**
     * Creates a metamodel validator.
     *
     * @param entityManagerFactory source of the managed JPA metamodel
     */
    public JpaSearchDefinitionValidator(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = Objects.requireNonNull(
                entityManagerFactory,
                "entityManagerFactory must not be null");
    }

    @Override
    public void validate(SearchDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        validateEntity(definition.entity());
        for (SearchField<?> field : definition.fields().values()) {
            if (field.filtering().enabled()) {
                validatePath(definition, field, "filtering", field.filtering().path());
            }
            if (field.sorting().enabled()) {
                validatePath(definition, field, "sorting", field.sorting().path());
            }
        }
    }

    private void validateEntity(Class<?> entity) {
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        try {
            metamodel.managedType(entity);
        } catch (IllegalArgumentException exception) {
            throw unresolvedEntity(entity, exception);
        }
    }

    private void validatePath(
            SearchDefinition<?> definition,
            SearchField<?> field,
            String capability,
            String path) {
        Class<?> pathRoot = field.subtype().orElse(definition.entity());
        validateSubtype(definition.entity(), pathRoot);
        Class<?> resolved = resolve(pathRoot, field.selector(), capability, path);
        Class<?> pathType = ClassUtils.resolvePrimitiveIfNecessary(resolved);
        Class<?> fieldType = ClassUtils.resolvePrimitiveIfNecessary(field.type());
        if (!pathType.equals(fieldType)) {
            throw unresolved(pathRoot, field.selector(), capability, path);
        }
    }

    private void validateSubtype(Class<?> entity, Class<?> pathRoot) {
        if (!entity.isAssignableFrom(pathRoot)) {
            throw unresolvedEntity(pathRoot, null);
        }
        validateEntity(pathRoot);
    }

    private Class<?> resolve(Class<?> entity, String selector, String capability, String path) {
        String[] segments = SearchPath.segments(path);
        Class<?> current = entity;
        for (int index = 0; index < segments.length; index++) {
            ManagedType<?> managedType = managedType(entity, selector, capability, path, current);
            Attribute<?, ?> attribute = attribute(entity, selector, capability, path, managedType, segments[index]);
            if (attribute.isCollection() && index < segments.length - 1) {
                if (!(attribute instanceof PluralAttribute<?, ?, ?> pluralAttribute)) {
                    throw unresolved(entity, selector, capability, path);
                }
                current = pluralAttribute.getElementType().getJavaType();
            } else {
                current = attribute.getJavaType();
            }
        }
        return current;
    }

    private ManagedType<?> managedType(
            Class<?> entity,
            String selector,
            String capability,
            String path,
            Class<?> type) {
        Metamodel metamodel = entityManagerFactory.getMetamodel();
        try {
            return metamodel.managedType(type);
        } catch (IllegalArgumentException exception) {
            throw unresolved(entity, selector, capability, path, exception);
        }
    }

    private Attribute<?, ?> attribute(
            Class<?> entity,
            String selector,
            String capability,
            String path,
            ManagedType<?> managedType,
            String segment) {
        try {
            return managedType.getAttribute(segment);
        } catch (IllegalArgumentException exception) {
            throw unresolved(entity, selector, capability, path, exception);
        }
    }

    private static SearchDefinitionValidationException unresolved(
            Class<?> entity,
            String selector,
            String capability,
            String path) {
        return unresolved(entity, selector, capability, path, null);
    }

    private static SearchDefinitionValidationException unresolved(
            Class<?> entity,
            String selector,
            String capability,
            String path,
            Throwable cause) {
        return new SearchDefinitionValidationException(
                SearchDefinitionValidationException.JPA_PATH_UNRESOLVED,
                "selector '%s' %s path '%s' could not be resolved against JPA entity '%s'"
                        .formatted(selector, capability, path, entity.getName()),
                cause);
    }

    private static SearchDefinitionValidationException unresolvedEntity(Class<?> entity, Throwable cause) {
        return new SearchDefinitionValidationException(
                SearchDefinitionValidationException.JPA_PATH_UNRESOLVED,
                "entity '%s' is not managed by JPA".formatted(entity.getName()),
                cause);
    }
}
