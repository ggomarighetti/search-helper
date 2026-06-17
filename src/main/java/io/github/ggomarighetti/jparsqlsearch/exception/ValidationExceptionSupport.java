package io.github.ggomarighetti.jparsqlsearch.exception;

import java.util.List;
import java.util.Objects;

final class ValidationExceptionSupport {
    private ValidationExceptionSupport() {
    }

    static String requireCode(String code) {
        if (Objects.requireNonNull(code, "code must not be null").isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code;
    }

    static <T> List<T> copyList(List<T> values, String parameterName) {
        return List.copyOf(Objects.requireNonNull(values, parameterName + " must not be null"));
    }
}
