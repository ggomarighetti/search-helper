package io.github.ggomarighetti.jparsqlsearch.integration;

import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresTestEnvironment;
import io.github.ggomarighetti.jparsqlsearch.jpa.JpaSearchDefinitionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDefinitionRuntimeValidationPostgresIT {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    autoConfiguration("JpaRsqlSearchRsqlAutoConfiguration"),
                    autoConfiguration("JpaRsqlSearchAutoConfiguration")))
            .withUserConfiguration(ProductJpaConfiguration.class)
            .withPropertyValues(PostgresTestEnvironment.springProperties())
            .withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    void doesNotValidateSearchDefinitionBeansOnStartup() {
        contextRunner
                .withUserConfiguration(InvalidDefinitionConfiguration.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(JpaSearchDefinitionValidator.class);
                    assertThat(context).doesNotHaveBean("searchDefinitionStartupValidator");
                });
    }

    private static Class<?> autoConfiguration(String simpleName) {
        try {
            return Class.forName(
                    "io.github.ggomarighetti.jparsqlsearch.autoconfigure." + simpleName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("missing auto-configuration " + simpleName, exception);
        }
    }

    @Test
    void validatesJpaPathsWhenRsqlFilterIsCompiled() {
        contextRunner
                .withUserConfiguration(InvalidDefinitionConfiguration.class)
                .run(context -> {
                    SearchCompiler compiler = context.getBean(SearchCompiler.class);
                    SearchDefinition<?> definition = context.getBean(SearchDefinition.class);
                    PageRequest pageRequest = PageRequest.of(0, 20);

                    assertThatThrownBy(() -> compiler.compile(
                                    "displayName==Laptop",
                                    null,
                                    pageRequest,
                                    definition))
                            .isInstanceOf(SearchDefinitionValidationException.class)
                            .hasMessageContaining("displayName")
                            .satisfies(exception -> assertThat(((SearchDefinitionValidationException) exception).code())
                                    .isEqualTo(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED));
                });
    }

    @Test
    void validatesJpaPathsWhenSearchWithoutRsqlFilterIsCompiled() {
        contextRunner
                .withUserConfiguration(InvalidDefinitionConfiguration.class)
                .run(context -> {
                    SearchCompiler compiler = context.getBean(SearchCompiler.class);
                    SearchDefinition<?> definition = context.getBean(SearchDefinition.class);
                    PageRequest pageRequest = PageRequest.of(0, 20);

                    assertThatThrownBy(() -> compiler.compile(
                                    null,
                                    null,
                                    pageRequest,
                                    definition))
                            .isInstanceOf(SearchDefinitionValidationException.class)
                            .hasMessageContaining("displayName")
                            .satisfies(exception -> assertThat(((SearchDefinitionValidationException) exception).code())
                                    .isEqualTo(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Product.class)
    static class ProductJpaConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidDefinitionConfiguration {
        @Bean
        SearchDefinition<Product> invalidProductSearch() {
            return SearchDefinition.builder().entity(Product.class)
                    .fields(fields -> fields.add("displayName", String.class)
                            .filterable(filter -> filter.allow(EQUAL)))
                    .build();
        }
    }
}
