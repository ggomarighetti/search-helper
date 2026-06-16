package io.github.ggomarighetti.searchhelper.definition;

import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import java.util.Objects;

/** Creates definition builders with application-wide path limits. */
public record SearchDefinitionFactory(SearchPolicy policy) {
    /**
     * Creates a factory.
     *
     * @param policy application-wide policy
     */
    public SearchDefinitionFactory {
        policy = Objects.requireNonNull(policy, "policy must not be null");
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
