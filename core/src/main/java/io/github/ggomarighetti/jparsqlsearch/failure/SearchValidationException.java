package io.github.ggomarighetti.jparsqlsearch.failure;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Base type for immutable validation exceptions raised while compiling a search request. */
public abstract class SearchValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Stable machine-readable validation error code. */
    private final String code;
    /** Immutable subtype-specific validation details. */
    private final List<? extends Serializable> details;

    /**
     * Creates a validation exception without an underlying cause.
     *
     * @param code stable machine-readable error code
     * @param message safe error message
     * @param details immutable-copied validation details
     */
    protected SearchValidationException(String code, String message, List<? extends Serializable> details) {
        this(code, message, details, null);
    }

    /**
     * Creates a validation exception with an optional underlying cause.
     *
     * @param code stable machine-readable error code
     * @param message safe error message
     * @param details immutable-copied validation details
     * @param cause underlying failure, or {@code null}
     */
    protected SearchValidationException(
            String code,
            String message,
            List<? extends Serializable> details,
            Throwable cause) {
        super(message, cause);
        this.code = requireCode(code);
        this.details = List.copyOf(Objects.requireNonNull(details, "details must not be null"));
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return error code
     */
    public final String code() {
        return code;
    }

    /**
     * Returns immutable validation details for subtype-specific accessors.
     *
     * @return immutable validation detail list
     */
    @SuppressWarnings("unchecked")
    protected final <T extends Serializable> List<T> details() {
        return (List<T>) details;
    }

    private static String requireCode(String code) {
        if (Objects.requireNonNull(code, "code must not be null").isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code;
    }
}
