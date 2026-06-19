package io.github.ggomarighetti.jparsqlsearch.rsql.jpa;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.util.Objects;

/**
 * Associates one logical RSQL operator with custom JPA execution.
 *
 * @param operator logical operator
 * @param predicateFactory custom predicate factory
 */
public record RsqlJpaOperatorBinding(
        RsqlOperator operator,
        RsqlJpaPredicateFactory predicateFactory) {
    /** Validates the binding. */
    public RsqlJpaOperatorBinding {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(predicateFactory, "predicateFactory must not be null");
    }
}
