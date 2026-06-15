package io.github.ggomarighetti.searchhelper.exception;

import java.io.Serializable;
import java.util.Objects;

/**
 * One semantic validation error located in the parsed RSQL AST.
 *
 * @param code stable machine-readable error code
 * @param astPath location of the failure in the parsed AST
 * @param selector public selector involved, or {@code null}
 * @param operator logical operator name involved, or {@code null}
 * @param argumentIndex zero-based argument index, or {@code null}
 * @param validationPath Bean Validation path, or {@code null}
 * @param message safe human-readable message
 * @param messageTemplate Bean Validation message template, or {@code null}
 * @param constraint Bean Validation constraint type, or {@code null}
 */
public record RsqlValidationError(
        String code,
        String astPath,
        String selector,
        String operator,
        Integer argumentIndex,
        String validationPath,
        String message,
        String messageTemplate,
        String constraint) implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String FIELD_NOT_ALLOWED = "RSQL_FIELD_NOT_ALLOWED";
    public static final String FILTERING_DISABLED = "RSQL_FILTERING_DISABLED";
    public static final String OPERATOR_NOT_ALLOWED = "RSQL_OPERATOR_NOT_ALLOWED";
    public static final String OPERATOR_INVALID_ARITY = "RSQL_OPERATOR_INVALID_ARITY";
    public static final String ARGUMENT_CONVERSION_FAILED = "RSQL_ARGUMENT_CONVERSION_FAILED";
    public static final String ARGUMENT_RULE_VIOLATION = "RSQL_ARGUMENT_RULE_VIOLATION";
    public static final String ARGUMENTS_RULE_VIOLATION = "RSQL_ARGUMENTS_RULE_VIOLATION";

    /** Validates required location and message fields. */
    public RsqlValidationError {
        code = requireText(code, "code");
        astPath = requireText(astPath, "astPath");
        message = requireText(message, "message");
        if (argumentIndex != null && argumentIndex < 0) {
            throw new IllegalArgumentException("argumentIndex must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
