package io.github.ggomarighetti.jparsqlsearch.rsql.operator;

import cz.jirutka.rsql.parser.ast.Arity;

/**
 * Inclusive number of arguments accepted by an operator.
 *
 * @param min minimum argument count
 * @param max maximum argument count
 */
public record RsqlOperatorArity(int min, int max) {
    /** Validates the inclusive arity range. */
    public RsqlOperatorArity {
        if (min < 0) {
            throw new IllegalArgumentException("min must not be negative");
        }
        if (max < min) {
            throw new IllegalArgumentException("max must be greater than or equal to min");
        }
    }

    /**
     * Creates an exact arity.
     *
     * @param arguments required argument count
     * @return exact arity
     */
    public static RsqlOperatorArity exact(int arguments) {
        return new RsqlOperatorArity(arguments, arguments);
    }

    /**
     * Creates a bounded inclusive arity.
     *
     * @param min minimum argument count
     * @param max maximum argument count
     * @return bounded arity
     */
    public static RsqlOperatorArity between(int min, int max) {
        return new RsqlOperatorArity(min, max);
    }

    /**
     * Creates an unbounded upper arity.
     *
     * @param min minimum argument count
     * @return arity accepting at least {@code min} arguments
     */
    public static RsqlOperatorArity atLeast(int min) {
        return new RsqlOperatorArity(min, Integer.MAX_VALUE);
    }

    /**
     * Tests an argument count.
     *
     * @param argumentCount observed argument count
     * @return {@code true} when the count is in range
     */
    public boolean accepts(int argumentCount) {
        return argumentCount >= min && argumentCount <= max;
    }

    Arity parserArity() {
        if (min == max) {
            return Arity.nary(min);
        }
        return Arity.of(min, max);
    }
}
