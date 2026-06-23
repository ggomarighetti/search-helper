package io.github.ggomarighetti.jparsqlsearch.rsql.validation;

import io.github.ggomarighetti.jparsqlsearch.failure.SearchValidationException;
import java.util.List;

/** Reports RSQL parse, policy, conversion, or semantic validation failures. */
public final class RsqlFilterValidationException extends SearchValidationException {
    private static final long serialVersionUID = 1L;

    /** The filter could not be parsed. */
    public static final String PARSE_ERROR = "RSQL_PARSE_ERROR";
    /** The filter violated the declared field or argument rules. */
    public static final String RULES_FORBIDDEN = "RSQL_RULES_FORBIDDEN";
    /** The filter exceeded a configured syntactic limit. */
    public static final String LIMIT_EXCEEDED = "RSQL_LIMIT_EXCEEDED";

    /**
     * Creates an exception without detailed errors or cause.
     *
     * @param code stable error code
     * @param message safe error message
     */
    public RsqlFilterValidationException(String code, String message) {
        this(code, message, List.of(), null);
    }

    /**
     * Creates an exception with an underlying cause.
     *
     * @param code stable error code
     * @param message safe error message
     * @param cause underlying parse or execution failure
     */
    public RsqlFilterValidationException(String code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    /**
     * Creates an exception with detailed semantic errors.
     *
     * @param code stable error code
     * @param message safe error message
     * @param errors immutable semantic errors
     */
    public RsqlFilterValidationException(
            String code,
            String message,
            List<RsqlValidationError> errors) {
        this(code, message, errors, null);
    }

    /**
     * Creates a fully detailed exception.
     *
     * @param code stable error code
     * @param message safe error message
     * @param errors immutable semantic errors
     * @param cause underlying failure, or {@code null}
     */
    public RsqlFilterValidationException(
            String code,
            String message,
            List<RsqlValidationError> errors,
            Throwable cause) {
        super(code, message, errors, cause);
    }

    /**
     * Returns detailed semantic errors.
     *
     * @return immutable semantic error list
     */
    public List<RsqlValidationError> errors() {
        return details();
    }
}
