import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.github.ggomarighetti.rsqljpasearch.compile.SearchCompiler;
import io.github.ggomarighetti.rsqljpasearch.definition.SearchDefinition;
import io.github.ggomarighetti.rsqljpasearch.policy.SearchPolicy;
import io.github.ggomarighetti.rsqljpasearch.protection.SearchProtectionException;
import io.github.ggomarighetti.rsqljpasearch.rsql.backend.perplexhub.PerplexhubRsqlEngines;
import io.github.ggomarighetti.rsqljpasearch.rsql.validation.RsqlFilterValidationException;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.PageRequest;

public final class RsqlSearchFuzzer {
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

    private RsqlSearchFuzzer() {}

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String rsql = new String(data.consumeBytes(MAX_INPUT_BYTES), StandardCharsets.UTF_8);
        try {
            COMPILER.compile(rsql, null, PAGE_REQUEST, DEFINITION);
        } catch (RsqlFilterValidationException | SearchProtectionException expected) {
            // Rejected fuzz input is a normal outcome; other throwables are findings.
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
