package io.github.ggomarighetti.searchhelper.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ggomarighetti.searchhelper.validation.RuleViolation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionSerializationTest {
    @Test
    void rsqlExceptionKeepsValidationErrorsAfterSerialization() throws Exception {
        RsqlFilterValidationException exception = new RsqlFilterValidationException(
                RsqlFilterValidationException.RULES_FORBIDDEN,
                "RSQL rejected",
                List.of(new RsqlValidationError(
                        RsqlValidationError.ARGUMENT_RULE_VIOLATION,
                        "$.left",
                        "name",
                        "eq",
                        0,
                        "name",
                        "must not be blank",
                        "{jakarta.validation.constraints.NotBlank.message}",
                        "jakarta.validation.constraints.NotBlank")));

        RsqlFilterValidationException copy = roundTrip(exception, RsqlFilterValidationException.class);

        assertEquals(exception.code(), copy.code());
        assertEquals(exception.getMessage(), copy.getMessage());
        assertEquals(exception.errors(), copy.errors());
    }

    @Test
    void pageableExceptionKeepsViolationsAfterSerialization() throws Exception {
        SearchPageableValidationException exception = new SearchPageableValidationException(
                SearchPageableValidationException.PAGE_RULES_FORBIDDEN,
                "Page rejected",
                List.of(new RuleViolation(
                        "page.size",
                        "must be less than or equal to 50",
                        "{jakarta.validation.constraints.Max.message}",
                        "jakarta.validation.constraints.Max")));

        SearchPageableValidationException copy = roundTrip(exception, SearchPageableValidationException.class);

        assertEquals(exception.code(), copy.code());
        assertEquals(exception.getMessage(), copy.getMessage());
        assertEquals(exception.violations(), copy.violations());
    }

    @Test
    void queryExceptionKeepsViolationsAfterSerialization() throws Exception {
        SearchQueryValidationException exception = new SearchQueryValidationException(
                SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                "Query rejected",
                List.of(new RuleViolation(
                        "query",
                        "size must be between 3 and 10",
                        "{jakarta.validation.constraints.Size.message}",
                        "jakarta.validation.constraints.Size")));

        SearchQueryValidationException copy = roundTrip(exception, SearchQueryValidationException.class);

        assertEquals(exception.code(), copy.code());
        assertEquals(exception.getMessage(), copy.getMessage());
        assertEquals(exception.violations(), copy.violations());
    }

    private static <T> T roundTrip(T value, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return type.cast(input.readObject());
        }
    }
}
