package io.github.ggomarighetti.jparsqlsearch.exception;

import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.List;

/** Reports pageable or sorting input rejected by definition or policy rules. */
public final class SearchPageableValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Sort selector or direction is not declared. */
    public static final String SORT_RULES_FORBIDDEN = "SORT_RULES_FORBIDDEN";
    /** Page or size violates definition rules. */
    public static final String PAGE_RULES_FORBIDDEN = "PAGE_RULES_FORBIDDEN";
    /** Sort exceeds global protection limits. */
    public static final String SORT_LIMIT_EXCEEDED = "SORT_LIMIT_EXCEEDED";
    /** Page, size, offset, or unpaged mode exceeds protection limits. */
    public static final String PAGE_LIMIT_EXCEEDED = "PAGE_LIMIT_EXCEEDED";

    /** Stable machine-readable error code. */
    private final String code;
    /** Immutable request validation violations. */
    private final List<RuleViolation> violations;

    /**
     * Creates an exception without Bean Validation details.
     *
     * @param code stable error code
     * @param message safe error message
     */
    public SearchPageableValidationException(String code, String message) {
        this(code, message, List.of());
    }

    /**
     * Creates an exception with page or size violations.
     *
     * @param code stable error code
     * @param message safe error message
     * @param violations immutable safe violations
     */
    public SearchPageableValidationException(
            String code,
            String message,
            List<RuleViolation> violations) {
        super(message);
        this.code = ValidationExceptionSupport.requireCode(code);
        this.violations = ValidationExceptionSupport.copyList(violations, "violations");
    }

    /**
     * Returns the error code.
     *
     * @return stable machine-readable error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns page and size violations.
     *
     * @return immutable safe violation list
     */
    public List<RuleViolation> violations() {
        return violations;
    }
}
