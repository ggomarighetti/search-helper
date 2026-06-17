package io.github.ggomarighetti.jparsqlsearch.rsql.operator;

import java.util.Objects;
import org.springframework.util.Assert;

/**
 * Stable logical identifier for an RSQL operator.
 *
 * @param name non-blank logical name
 */
public record RsqlOperator(String name) {
    /** Normalizes and validates the operator name. */
    public RsqlOperator {
        Assert.hasText(name, "name must not be blank");
        name = Objects.requireNonNull(name, "name must not be null").trim();
    }

    /**
     * Creates an operator identifier.
     *
     * @param name non-blank logical name
     * @return operator identifier
     */
    public static RsqlOperator of(String name) {
        return new RsqlOperator(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
