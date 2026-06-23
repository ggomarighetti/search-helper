package io.github.ggomarighetti.jparsqlsearch.rsql.jpa;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import java.util.List;
import java.util.Objects;

/**
 * JPA state and converted arguments supplied to a custom operator predicate.
 *
 * @param <P> Java type of the resolved path
 * @param <A> declaring type of the metamodel attribute
 * @param <B> Java type of the metamodel attribute
 * @param <R> source type of the JPA from/root
 * @param <J> Java type selected by the JPA from/root
 * @param criteriaBuilder criteria builder used to create predicates
 * @param path resolved JPA path for the public selector
 * @param attribute JPA metamodel attribute backing the selector
 * @param arguments converted RSQL argument values
 * @param root JPA root/from used by the compiled specification
 * @param operator matched RSQL operator
 */
public record RsqlJpaPredicateContext<P, A, B, R, J>(
        CriteriaBuilder criteriaBuilder,
        Path<P> path,
        Attribute<A, B> attribute,
        List<Object> arguments,
        From<R, J> root,
        RsqlOperator operator) {
    /** Creates a custom-predicate context. */
    public RsqlJpaPredicateContext {
        Objects.requireNonNull(criteriaBuilder, "criteriaBuilder must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(attribute, "attribute must not be null");
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
