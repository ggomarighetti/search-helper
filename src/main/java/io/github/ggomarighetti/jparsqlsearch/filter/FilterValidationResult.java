package io.github.ggomarighetti.jparsqlsearch.filter;

import java.util.List;
import java.util.Objects;

/**
 * Result of converting and validating all arguments for one filter operator.
 *
 * @param errors immutable conversion and validation errors
 */
public record FilterValidationResult(List<FilterValidationError> errors) {
    /** Copies the supplied errors into an immutable list. */
    public FilterValidationResult {
        errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
    }

    /**
     * Reports whether validation succeeded.
     *
     * @return {@code true} when every argument was converted and validated
     */
    public boolean accepted() {
        return errors.isEmpty();
    }
}
