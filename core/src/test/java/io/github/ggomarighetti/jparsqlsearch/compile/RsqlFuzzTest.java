package io.github.ggomarighetti.jparsqlsearch.compile;

import static org.junit.jupiter.api.Assertions.fail;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.property.SearchPropertyFixtures;
import io.github.ggomarighetti.jparsqlsearch.unit.TestRsqlEngines;
import java.nio.charset.StandardCharsets;

class RsqlFuzzTest {
    private static final int MAX_INPUT_BYTES = 16_384;
    private static final RsqlSearchGuard GUARD = new RsqlSearchGuard(TestRsqlEngines.defaults());
    private static final SearchDefinition<Product> DEFINITION = SearchPropertyFixtures.rsqlDefinition();

    @FuzzTest(maxDuration = "30s")
    void arbitraryInputNeverEscapesUnexpectedThrowable(byte[] bytes) {
        if (bytes.length > MAX_INPUT_BYTES) {
            return;
        }

        String input = new String(bytes, StandardCharsets.UTF_8);
        try {
            GUARD.specification(input, DEFINITION);
        } catch (Throwable throwable) {
            if (!SearchPropertyFixtures.isExpectedRsqlThrowable(throwable)) {
                fail("Unexpected throwable for fuzzed RSQL input", throwable);
            }
        }
    }
}
