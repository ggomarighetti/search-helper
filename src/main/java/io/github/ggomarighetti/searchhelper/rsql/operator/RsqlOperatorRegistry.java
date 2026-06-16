package io.github.ggomarighetti.searchhelper.rsql.operator;

import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable lookup registry for logical operators and parser symbols. */
public final class RsqlOperatorRegistry {
    private final Map<RsqlOperator, RsqlOperatorDescriptor> byOperator;
    private final Map<String, RsqlOperatorDescriptor> bySymbol;
    private final Set<ComparisonOperator> parserOperators;

    /**
     * Creates a registry and rejects duplicate operators or symbols.
     *
     * @param descriptors descriptors to register
     */
    public RsqlOperatorRegistry(Collection<RsqlOperatorDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors must not be null");
        Map<RsqlOperator, RsqlOperatorDescriptor> operators = new LinkedHashMap<>();
        Map<String, RsqlOperatorDescriptor> symbols = new LinkedHashMap<>();
        Set<ComparisonOperator> parsedOperators = new LinkedHashSet<>();
        for (RsqlOperatorDescriptor descriptor : descriptors) {
            RsqlOperatorDescriptor previous = operators.putIfAbsent(descriptor.operator(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException("operator '%s' is already registered".formatted(descriptor.operator()));
            }
            for (String symbol : descriptor.symbols()) {
                RsqlOperatorDescriptor previousSymbol = symbols.putIfAbsent(symbol, descriptor);
                if (previousSymbol != null) {
                    throw new IllegalArgumentException("RSQL symbol '%s' is already registered".formatted(symbol));
                }
            }
            parsedOperators.add(descriptor.comparisonOperator());
        }
        this.byOperator = Collections.unmodifiableMap(operators);
        this.bySymbol = Collections.unmodifiableMap(symbols);
        this.parserOperators = Collections.unmodifiableSet(parsedOperators);
    }

    /**
     * Returns registered descriptors.
     *
     * @return immutable descriptors in registration order
     */
    public Collection<RsqlOperatorDescriptor> descriptors() {
        return byOperator.values();
    }

    /**
     * Returns parser-native operators.
     *
     * @return immutable parser-native operators
     */
    public Set<ComparisonOperator> parserOperators() {
        return parserOperators;
    }

    /**
     * Looks up a logical operator.
     *
     * @param operator logical identifier
     * @return matching descriptor
     */
    public Optional<RsqlOperatorDescriptor> descriptor(RsqlOperator operator) {
        return Optional.ofNullable(byOperator.get(operator));
    }

    /**
     * Requires a registered logical operator.
     *
     * @param operator logical identifier
     * @return matching descriptor
     * @throws IllegalArgumentException when the operator is not registered
     */
    public RsqlOperatorDescriptor require(RsqlOperator operator) {
        return descriptor(operator).orElseThrow(() ->
                new IllegalArgumentException("operator '%s' is not registered".formatted(operator)));
    }

    /**
     * Looks up a parser-native operator by any symbol.
     *
     * @param comparisonOperator parser-native operator
     * @return matching descriptor
     */
    public Optional<RsqlOperatorDescriptor> descriptor(ComparisonOperator comparisonOperator) {
        Objects.requireNonNull(comparisonOperator, "comparisonOperator must not be null");
        for (String symbol : comparisonOperator.getSymbols()) {
            RsqlOperatorDescriptor descriptor = bySymbol.get(symbol);
            if (descriptor != null) {
                return Optional.of(descriptor);
            }
        }
        return Optional.empty();
    }
}
