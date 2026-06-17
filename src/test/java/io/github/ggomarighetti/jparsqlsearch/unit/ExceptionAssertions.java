package io.github.ggomarighetti.jparsqlsearch.unit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

public final class ExceptionAssertions {
    private ExceptionAssertions() {
    }

    public static <T extends Throwable> T thrownBy(Class<T> expectedType, Executable executable) {
        return Assertions.assertThrows(expectedType, executable);
    }
}
