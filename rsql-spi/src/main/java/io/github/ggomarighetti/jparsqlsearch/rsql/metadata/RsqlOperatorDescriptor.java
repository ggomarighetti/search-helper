package io.github.ggomarighetti.jparsqlsearch.rsql.metadata;

import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.Assert;

/** Parser, conversion, and arity metadata for an operator. */
public final class RsqlOperatorDescriptor {
    private final RsqlOperator operator;
    private final Set<String> symbols;
    private final RsqlOperatorArity arity;
    private final Class<?> argumentType;

    private RsqlOperatorDescriptor(
            RsqlOperator operator,
            Set<String> symbols,
            RsqlOperatorArity arity,
            Class<?> argumentType) {
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.symbols = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(symbols, "symbols must not be null")));
        this.arity = Objects.requireNonNull(arity, "arity must not be null");
        this.argumentType = argumentType;
    }

    /**
     * Creates a descriptor builder.
     *
     * @param operator logical operator identifier
     * @return descriptor builder
     */
    public static Builder builder(RsqlOperator operator) {
        return new Builder(operator);
    }

    /**
     * Creates a single-symbol descriptor with one argument.
     *
     * @param operator logical operator identifier
     * @param symbol parser symbol
     * @return descriptor
     */
    public static RsqlOperatorDescriptor of(RsqlOperator operator, String symbol) {
        return builder(operator).symbol(symbol).build();
    }

    /**
     * Returns the logical identifier.
     *
     * @return logical operator identifier
     */
    public RsqlOperator operator() {
        return operator;
    }

    /**
     * Returns every parser alias.
     *
     * @return immutable parser symbols
     */
    public Set<String> symbols() {
        return symbols;
    }

    /**
     * Returns the primary parser symbol.
     *
     * @return primary parser symbol
     */
    public String symbol() {
        return symbols.iterator().next();
    }

    /**
     * Returns accepted argument counts.
     *
     * @return accepted argument arity
     */
    public RsqlOperatorArity arity() {
        return arity;
    }

    /**
     * Returns the explicit custom argument type.
     *
     * @return explicit conversion type for custom execution
     */
    public Optional<Class<?>> argumentType() {
        return Optional.ofNullable(argumentType);
    }

    /**
     * Creates the parser representation.
     *
     * @return parser-native operator using this descriptor's symbols and arity
     */
    public ComparisonOperator comparisonOperator() {
        return new ComparisonOperator(symbols.toArray(String[]::new), arity.parserArity());
    }

    /** Builder for an immutable operator descriptor. */
    public static final class Builder {
        private final RsqlOperator operator;
        private final Set<String> symbols = new LinkedHashSet<>();
        private RsqlOperatorArity arity = RsqlOperatorArity.exact(1);
        private Class<?> argumentType;

        private Builder(RsqlOperator operator) {
            this.operator = Objects.requireNonNull(operator, "operator must not be null");
        }

        /**
         * Adds a parser symbol.
         *
         * @param symbol non-blank RSQL symbol
         * @return this builder
         */
        public Builder symbol(String symbol) {
            Assert.hasText(symbol, "symbol must not be blank");
            symbols.add(symbol);
            return this;
        }

        /**
         * Adds parser aliases.
         *
         * @param symbols non-empty parser symbols
         * @return this builder
         */
        public Builder symbols(String... symbols) {
            Assert.notEmpty(symbols, "symbols must not be empty");
            for (String symbol : symbols) {
                symbol(symbol);
            }
            return this;
        }

        /**
         * Configures accepted argument counts.
         *
         * @param arity inclusive arity
         * @return this builder
         */
        public Builder arity(RsqlOperatorArity arity) {
            this.arity = Objects.requireNonNull(arity, "arity must not be null");
            return this;
        }

        /**
         * Configures an exact argument count.
         *
         * @param arguments required argument count
         * @return this builder
         */
        public Builder exactArguments(int arguments) {
            return arity(RsqlOperatorArity.exact(arguments));
        }

        /**
         * Configures the conversion type used by a custom predicate.
         *
         * @param argumentType conversion type
         * @return this builder
         */
        public Builder argumentType(Class<?> argumentType) {
            this.argumentType = Objects.requireNonNull(argumentType, "argumentType must not be null");
            return this;
        }

        /**
         * Builds the descriptor.
         *
         * @return immutable descriptor
         */
        public RsqlOperatorDescriptor build() {
            if (symbols.isEmpty()) {
                throw new IllegalArgumentException("operator '%s' must declare at least one symbol".formatted(operator));
            }
            return new RsqlOperatorDescriptor(
                    operator,
                    symbols,
                    arity,
                    argumentType);
        }
    }
}
