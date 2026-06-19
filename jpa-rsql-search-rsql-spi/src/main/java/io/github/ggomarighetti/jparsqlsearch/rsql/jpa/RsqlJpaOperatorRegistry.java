package io.github.ggomarighetti.jparsqlsearch.rsql.jpa;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry of custom JPA operator bindings. */
public final class RsqlJpaOperatorRegistry {
    private final Map<RsqlOperator, RsqlJpaOperatorBinding> bindings;

    /**
     * Creates a registry and rejects duplicate operator bindings.
     *
     * @param bindings bindings to register
     */
    public RsqlJpaOperatorRegistry(Collection<RsqlJpaOperatorBinding> bindings) {
        Objects.requireNonNull(bindings, "bindings must not be null");
        Map<RsqlOperator, RsqlJpaOperatorBinding> registered = new LinkedHashMap<>();
        for (RsqlJpaOperatorBinding binding : bindings) {
            RsqlJpaOperatorBinding previous = registered.putIfAbsent(binding.operator(), binding);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "JPA predicate for operator '%s' is already registered".formatted(binding.operator()));
            }
        }
        this.bindings = Collections.unmodifiableMap(registered);
    }

    /**
     * Looks up custom execution for an operator.
     *
     * @param operator logical operator
     * @return predicate factory when bound
     */
    public Optional<RsqlJpaPredicateFactory> predicate(RsqlOperator operator) {
        RsqlJpaOperatorBinding binding = bindings.get(operator);
        return binding == null ? Optional.empty() : Optional.of(binding.predicateFactory());
    }

    /**
     * Returns bindings in registration order.
     *
     * @return immutable bindings
     */
    public Collection<RsqlJpaOperatorBinding> bindings() {
        return bindings.values();
    }
}
