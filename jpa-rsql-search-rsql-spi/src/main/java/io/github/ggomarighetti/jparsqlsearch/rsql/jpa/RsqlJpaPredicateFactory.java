package io.github.ggomarighetti.jparsqlsearch.rsql.jpa;

import jakarta.persistence.criteria.Predicate;

/**
 * Creates a JPA predicate for a custom RSQL operator.
 */
@FunctionalInterface
public interface RsqlJpaPredicateFactory {
    /**
     * Builds a predicate from the resolved path and converted arguments.
     *
     * @param context custom predicate context
     * @return JPA predicate
     */
    Predicate toPredicate(RsqlJpaPredicateContext<?, ?, ?, ?, ?> context);
}
