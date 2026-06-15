package io.github.ggomarighetti.searchhelper.autoconfigure;

import io.github.ggomarighetti.searchhelper.compile.SearchCompiler;
import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.definition.SearchDefinitionFactory;
import io.github.ggomarighetti.searchhelper.exception.SearchProtectionException;
import io.github.ggomarighetti.searchhelper.integration.bench.domain.Product;
import io.github.ggomarighetti.searchhelper.unit.TestTypes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.IN;

class SearchHelperAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SearchRsqlAutoConfiguration.class,
                    SearchHelperAutoConfiguration.class));

    @Test
    void createsSearchCompilerBean() {
        contextRunner.run(context -> assertNotNull(context.getBean(SearchCompiler.class)));
    }

    @Test
    void doesNotExposePostgresQueryExecutionConfiguration() throws IOException {
        assertFalse(Arrays.stream(SearchHelperProperties.class.getMethods())
                .anyMatch(method -> method.getName().equals("getPostgres")));

        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (var imports = SearchHelperAutoConfigurationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(imports);
            String autoConfigurations = new String(imports.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(autoConfigurations.contains("PostgresSearchAutoConfiguration"));
        }
    }

    @Test
    void createsSearchDefinitionFactoryWithGlobalPathLimits() {
        contextRunner
                .withPropertyValues("search.helper.paths.max-depth=4")
                .run(context -> {
                    SearchDefinitionFactory factory = context.getBean(SearchDefinitionFactory.class);

                    SearchDefinition<TestTypes.Product> definition = factory.builder()
                            .entity(TestTypes.Product.class)
                            .fields(fields -> fields.add("countryCode", String.class)
                                    .path("customer.region.country.code")
                                    .sortable())
                            .build();

                    assertTrue(definition.sortingPaths().containsKey("countryCode"));
                });
    }

    @Test
    void bindsGlobalProtectionProperties() {
        contextRunner
                .withPropertyValues("search.helper.filter.max-in-values=1")
                .run(context -> {
                    SearchDefinitionFactory factory = context.getBean(SearchDefinitionFactory.class);
                    SearchCompiler compiler = context.getBean(SearchCompiler.class);
                    SearchDefinition<TestTypes.Product> definition = factory.builder()
                            .entity(TestTypes.Product.class)
                            .fields(fields -> fields.add("taxId", String.class)
                                    .path("owner.taxIdentifier")
                                    .filterable(filter -> filter.allow(IN)))
                            .paging()
                            .build();

                    assertThrows(
                            SearchProtectionException.class,
                            () -> compiler.compile(
                                    "taxId=in=(1,2)",
                                    null,
                                    org.springframework.data.domain.PageRequest.of(0, 25),
                                    definition));
                });
    }

    @Test
    void doesNotRequireSearchDefinitionBeans() {
        contextRunner
                .run(context -> {
                    SearchDefinitionFactory factory = context.getBean(SearchDefinitionFactory.class);
                    SearchDefinition<TestTypes.Product> definition = factory.builder()
                            .entity(TestTypes.Product.class)
                            .fields(fields -> fields.add("name", String.class))
                            .build();

                    assertFalse(context.getBeansOfType(SearchDefinition.class).containsValue(definition));
                    assertTrue(definition.fields().containsKey("name"));
                });
    }
}
