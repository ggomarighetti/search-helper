package io.github.ggomarighetti.jparsqlsearch.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Serializable view of a Jakarta Bean Validation violation.
 *
 * <p>The invalid value is intentionally omitted so validation responses do not
 * accidentally expose credentials, identifiers, or other request data.
 *
 * @param path property path reported by Bean Validation
 * @param message interpolated validation message
 * @param messageTemplate original validation message template
 * @param constraint fully qualified constraint annotation type
 */
public record RuleViolation(
        String path,
        String message,
        String messageTemplate,
        String constraint) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Validates all safe violation fields. */
    public RuleViolation {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(messageTemplate, "messageTemplate must not be null");
        Objects.requireNonNull(constraint, "constraint must not be null");
    }

    /**
     * Copies this violation with a replacement path.
     *
     * @param path replacement path
     * @return copied violation
     */
    public RuleViolation withPath(String path) {
        return new RuleViolation(path, message, messageTemplate, constraint);
    }

    /**
     * Prefixes the current path.
     *
     * @param prefix path prefix
     * @return copied violation with the prefixed path
     */
    public RuleViolation prefixed(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return withPath(path.isBlank() ? prefix : prefix + "." + path);
    }
}
