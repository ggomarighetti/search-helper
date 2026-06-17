package io.github.ggomarighetti.jparsqlsearch.definition;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.Objects;

/**
 * Creates definition builders with application-wide path limits.
 *
 * @param policy application-wide policy
 */
public record SearchDefinitionFactory(SearchPolicy policy) {
    /**
     * Creates a factory.
     *
     * @param policy application-wide policy
     */
    public SearchDefinitionFactory {
        Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * Creates a definition builder.
     *
     * @return definition builder initialized with this factory's policy
     */
    public SearchDefinition.EntityStep builder() {
        return SearchDefinition.builder(policy);
    }
}
