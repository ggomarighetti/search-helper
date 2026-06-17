package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DocumentationPolicyExamplesTest {
    @Test
    void readmePolicyExampleCompiles() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(limits -> limits
                        .rsql(rsql -> rsql
                                .maxLength(2048))
                        .filter(filter -> filter
                                .maxArgumentsPerComparison(25))
                        .paging(paging -> paging
                                .maxPage(50)
                                .maxSize(50))
                        .sorting(sorting -> sorting.maxOrders(2))
                        .query(query -> query.maxLength(100)))
                .build();

        assertNotNull(definition);
    }

    @Test
    void restPolicyExampleCompiles() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(limits -> limits
                        .rsql(rsql -> rsql
                                .maxLength(2048))
                        .filter(filter -> filter
                                .maxComparisons(16)
                                .maxArgumentsPerComparison(25))
                        .paging(paging -> paging
                                .maxPage(50)
                                .maxSize(50)
                                .maxOffset(2500))
                        .sorting(sorting -> sorting.maxOrders(2))
                        .query(query -> query.maxLength(100))
                        .paths(paths -> paths.maxDepth(3)))
                .build();

        assertNotNull(definition);
    }
}
