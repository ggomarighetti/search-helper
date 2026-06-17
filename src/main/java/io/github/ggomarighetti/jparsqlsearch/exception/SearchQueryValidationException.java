package io.github.ggomarighetti.jparsqlsearch.exception;

import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.List;

/** Indicates that free-text query validation failed. */
public final class SearchQueryValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Error code used when a query is attempted without permitted validation rules. */
    public static final String QUERY_RULES_FORBIDDEN = "QUERY_RULES_FORBIDDEN";

    /** Stable machine-readable error code. */
    private final String code;
    /** Immutable query validation violations. */
    private final List<RuleViolation> violations;

    /**
     * Creates an exception without rule violations or a cause.
     *
     * @param code stable error code
     * @param message detail message
     */
    public SearchQueryValidationException(String code, String message) {
        this(code, message, List.of(), null);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param code stable error code
     * @param message detail message
     * @param cause underlying cause
     */
    public SearchQueryValidationException(String code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    /**
     * Creates an exception with validation violations.
     *
     * @param code stable error code
     * @param message detail message
     * @param violations immutable-copied violation details
     */
    public SearchQueryValidationException(
            String code,
            String message,
            List<RuleViolation> violations) {
        this(code, message, violations, null);
    }

    /**
     * Creates an exception with validation violations and a cause.
     *
     * @param code stable error code
     * @param message detail message
     * @param violations immutable-copied violation details
     * @param cause underlying cause
     */
    public SearchQueryValidationException(
            String code,
            String message,
            List<RuleViolation> violations,
            Throwable cause) {
        super(message, cause);
        this.code = ValidationExceptionSupport.requireCode(code);
        this.violations = ValidationExceptionSupport.copyList(violations, "violations");
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns validation violations.
     *
     * @return immutable violation list
     */
    public List<RuleViolation> violations() {
        return violations;
    }
}
