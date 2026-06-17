package io.github.ggomarighetti.jparsqlsearch.rsql;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.util.List;
import java.util.Objects;
import org.springframework.util.Assert;

/**
 * Normalized comparison extracted from a parsed RSQL tree.
 *
 * @param selector public field selector
 * @param operator registered logical operator
 * @param arguments immutable raw argument list
 */
public record RsqlComparison(String selector, RsqlOperator operator, List<String> arguments) {
    /** Validates and snapshots comparison data. */
    public RsqlComparison {
        Assert.hasText(selector, "selector must not be blank");
        Objects.requireNonNull(operator, "operator must not be null");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    }
}
