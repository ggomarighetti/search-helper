package io.github.ggomarighetti.jparsqlsearch.exception;

/** Reports an invalid search definition or incompatible engine/backend setup. */
public final class SearchDefinitionValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** A declared path exceeds policy depth. */
    public static final String PATH_LIMIT_EXCEEDED = "PATH_LIMIT_EXCEEDED";
    /** A declared path cannot be resolved in the JPA metamodel. */
    public static final String JPA_PATH_UNRESOLVED = "JPA_PATH_UNRESOLVED";
    /** A validator or converter failed because of application configuration. */
    public static final String RSQL_CONFIGURATION_INVALID = "RSQL_CONFIGURATION_INVALID";
    /** A definition uses an operator absent from the engine. */
    public static final String RSQL_OPERATOR_NOT_REGISTERED = "RSQL_OPERATOR_NOT_REGISTERED";
    /** The active backend cannot execute a declared operator. */
    public static final String RSQL_OPERATOR_NOT_EXECUTABLE = "RSQL_OPERATOR_NOT_EXECUTABLE";
    /** Validation and execution argument types differ. */
    public static final String RSQL_OPERATOR_TYPE_MISMATCH = "RSQL_OPERATOR_TYPE_MISMATCH";
    /** No restrictive default operator profile exists for the field type. */
    public static final String DEFAULT_OPERATORS_UNSUPPORTED_TYPE = "DEFAULT_OPERATORS_UNSUPPORTED_TYPE";

    /** Stable machine-readable error code. */
    private final String code;

    /**
     * Creates a definition error.
     *
     * @param code stable error code
     * @param message safe error message
     */
    public SearchDefinitionValidationException(String code, String message) {
        super(message);
        this.code = ValidationExceptionSupport.requireCode(code);
    }

    /**
     * Creates a definition error with a cause.
     *
     * @param code stable error code
     * @param message safe error message
     * @param cause underlying configuration failure
     */
    public SearchDefinitionValidationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = ValidationExceptionSupport.requireCode(code);
    }

    /**
     * Returns the error code.
     *
     * @return stable machine-readable error code
     */
    public String code() {
        return code;
    }
}
