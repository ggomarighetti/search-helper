package io.github.ggomarighetti.searchhelper.rsql.backend;

import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import java.util.List;
import java.util.Objects;

/** JPA state and converted arguments supplied to a custom operator predicate. */
public record RsqlJpaPredicateContext(
        CriteriaBuilder criteriaBuilder,
        Path<?> path,
        Attribute<?, ?> attribute,
        List<Object> arguments,
        From<?, ?> root,
        RsqlOperator operator) {
    /** Creates a custom-predicate context. */
    public RsqlJpaPredicateContext {
        Objects.requireNonNull(criteriaBuilder, "criteriaBuilder must not be null");
        Objects.requireNonNull(path, "path must not be null");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
    }

    /**
     * Returns one converted argument.
     *
     * @param index zero-based argument index
     * @return converted argument
     */
    public Object argument(int index) {
        return arguments.get(index);
    }
}
