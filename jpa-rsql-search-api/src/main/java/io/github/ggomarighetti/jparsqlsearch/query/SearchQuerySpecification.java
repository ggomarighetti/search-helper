package io.github.ggomarighetti.jparsqlsearch.query;

import org.springframework.data.jpa.domain.Specification;

/**
 * Builds an entity specification from validated free-text input.
 *
 * @param <T> entity type
 */
@FunctionalInterface
public interface SearchQuerySpecification<T> {
    /**
     * Converts query text into a JPA specification.
     *
     * @param query validated query text
     * @return specification to execute
     */
    Specification<T> toSpecification(String query);
}
