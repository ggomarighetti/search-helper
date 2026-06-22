package io.github.ggomarighetti.jparsqlsearch.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlEngines;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.PageRequest;

class RsqlSearchFuzzTest {
    private static final int MAX_INPUT_BYTES = 16_384;
    private static final SearchCompiler COMPILER =
            new SearchCompiler(PerplexhubRsqlEngines.defaults(), SearchPolicy.defaults());
    private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 20);
    private static final SearchDefinition<Product> DEFINITION = SearchDefinition.builder()
            .entity(Product.class)
            .fields(fields -> {
                fields.add("sku", String.class).filterable();
                fields.add("name", String.class).filterable();
                fields.add("stock", Integer.class).filterable();
            })
            .paging()
            .build();

    @FuzzTest(maxDuration = "2m")
    void compileUntrustedRsql(FuzzedDataProvider data) {
        byte[] bytes = data.consumeBytes(MAX_INPUT_BYTES);
        String input = new String(bytes, StandardCharsets.UTF_8);

        try {
            COMPILER.compile(input, null, PAGE_REQUEST, DEFINITION);
        } catch (RsqlFilterValidationException | SearchProtectionException expected) {
            // Rejected input is a normal outcome. Any other throwable is a finding.
        }
    }

    static final class Product {
        private String sku;
        private String name;
        private Integer stock;

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public Integer getStock() {
            return stock;
        }
    }
}
