package io.github.ggomarighetti.jparsqlsearch.integration;

import io.github.ggomarighetti.jparsqlsearch.compile.CompiledSearch;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.page.validation.SearchPageableValidationException;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductRepository;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductSeeder.Catalog;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresTestEnvironment;
import io.github.ggomarighetti.jparsqlsearch.jpa.JpaSearchDefinitionValidator;
import io.github.ggomarighetti.jparsqlsearch.autoconfigure.SearchRsqlEngineCustomizer;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import java.util.List;
import org.springframework.boot.convert.ApplicationConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductSeeder.seedCatalog;
import static io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductSpecifications.publishedProducts;
import static io.github.ggomarighetti.jparsqlsearch.integration.ProductSearchFixtures.standardProductSearch;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(
        showSql = false,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProductSearchTestApplication.class)
@Import({
        ProductSearchPostgresIT.CatalogCodeSearchConfiguration.class
})
class ProductSearchPostgresIT {
    private static final RsqlOperator CATEGORY_CODE = RsqlOperator.of("CATEGORY_CODE");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        PostgresTestEnvironment.register(registry);
    }

    @Autowired
    private ProductRepository products;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SearchCompiler compiler;

    private Catalog catalog;

    @BeforeEach
    void seedProducts() {
        catalog = seedCatalog(entityManager);
    }

    @Test
    void compilesAndExecutesAStandardProductSearchUseCase() {
        Product gaming = catalog.gamingLaptop();
        String filter = "categoryCode==%s;supplierCountry==%s;price=ge=%s;active==true"
                .formatted(catalog.laptops().getCode(), catalog.acme().getAddress().getCountryCode(),
                        gaming.getPrice());

        CompiledSearch<Product> compiled = compiler.compile(
                filter,
                "laptop",
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("price"))),
                standardProductSearch(),
                publishedProducts());

        Page<Product> page = products.findAll(compiled.specification(), compiled.pageable());

        assertEquals(2, page.getTotalElements());
        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactly(catalog.workstationLaptop().getSku(), gaming.getSku());
    }

    @Test
    void filtersThroughManyToOneRelations() {
        Product gaming = catalog.gamingLaptop();
        String filter = "categoryCode==%s;supplierName==\"%s\";supplierPreferred==%s"
                .formatted(catalog.laptops().getCode(), catalog.acme().getName(), catalog.acme().getPreferred());

        Page<Product> page = search(
                filter,
                null,
                PageRequest.of(0, 10, Sort.by("sku")),
                standardProductSearch(),
                publishedProducts());

        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder(gaming.getSku(), catalog.workstationLaptop().getSku());
    }

    @Test
    void filtersThroughEmbeddedValuesAndNestedEmbeddedValues() {
        Product gaming = catalog.gamingLaptop();
        String filter = "weightKg=le=%s;createdBy==%s;supplierCountry==%s"
                .formatted(gaming.getDimensions().getWeightKg(), gaming.getAudit().getCreatedBy(),
                        catalog.acme().getAddress().getCountryCode());

        Page<Product> page = search(
                filter,
                null,
                PageRequest.of(0, 10, Sort.by("sku")),
                standardProductSearch(),
                publishedProducts());

        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactly(gaming.getSku());
    }

    @Test
    void filtersThroughOneToManyPathWhenTheUseCaseOptsIn() {
        Product gaming = catalog.gamingLaptop();
        Page<Product> page = search(
                "reviewRating=ge=4",
                null,
                PageRequest.of(0, 10, Sort.by("sku")),
                standardProductSearch(),
                publishedProducts());

        assertEquals(3, page.getTotalElements());
        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder(gaming.getSku(), catalog.workstationLaptop().getSku(),
                        catalog.phone().getSku());
    }

    @Test
    void onlyExposesPublishedProducts() {
        Page<Product> page = search(
                null,
                "laptop",
                PageRequest.of(0, 10, Sort.by("sku")),
                standardProductSearch(),
                publishedProducts());

        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactly(
                        catalog.gamingLaptop().getSku(),
                        catalog.officeLaptop().getSku(),
                        catalog.workstationLaptop().getSku());
    }

    @Test
    void convertsCommonValueTypesWithARealJpaRepository() {
        Product gaming = catalog.gamingLaptop();
        String filter = ("internalId==%d;publicId==%s;price==%s;stock==%d;active==true;"
                + "releasedOn==%s;createdAt==\"%s\"")
                .formatted(gaming.getId(), gaming.getPublicId(), gaming.getPrice(), gaming.getStock(),
                        gaming.getReleaseDate(), gaming.getAudit().getCreatedAt());

        Page<Product> page = search(
                filter,
                null,
                PageRequest.of(0, 10),
                standardProductSearch(),
                publishedProducts());

        assertThat(page.getContent())
                .extracting(Product::getSku)
                .containsExactly(gaming.getSku());
    }

    @Test
    void passesEngineConversionServiceToPerplexhubDuringJpaExecution() {
        SearchDefinition<Product> definition = SearchDefinition.builder().entity(Product.class)
                .fields(fields -> fields.add("categoryCode", String.class)
                        .filterable(filter -> filter.path("category.code")
                                .allow(CATEGORY_CODE, CatalogCode.class, operator -> {})))
                .paging()
                .build();
        CompiledSearch<Product> compiled = compiler.compile(
                "categoryCode=categoryCode=catalog_LAPTOP",
                null,
                PageRequest.of(0, 10),
                definition,
                publishedProducts());
        List<Product> results = products.findAll(
                compiled.specification(),
                compiled.pageable()).getContent();

        assertThat(results)
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder(
                        catalog.gamingLaptop().getSku(),
                        catalog.officeLaptop().getSku(),
                        catalog.workstationLaptop().getSku());
    }

    @Test
    void treatsWildcardCharactersAsLiteralStringFilterValues() {
        Page<Product> page = search(
                "name==*Laptop*",
                null,
                PageRequest.of(0, 10),
                standardProductSearch(),
                publishedProducts());

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void rejectsUnsafeInputsBeforeTheRepositoryExecutes() {
        SearchDefinition<Product> definition = standardProductSearch();

        assertRsqlCode("passwordHash==abc", definition, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertRsqlCode("price!=1899.99", definition, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertRsqlCode("status==PUBLISHED", definition, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertRsqlCode("publicId==not-a-uuid", definition, RsqlFilterValidationException.RULES_FORBIDDEN);

        PageRequest unsafeSort = PageRequest.of(0, 10, Sort.by("internalCost"));
        SearchPageableValidationException sortException = assertThrows(
                SearchPageableValidationException.class,
                () -> compiler.compile(
                        null,
                        null,
                        unsafeSort,
                        definition));
        PageRequest unsafePage = PageRequest.of(0, 51);
        SearchPageableValidationException pageException = assertThrows(
                SearchPageableValidationException.class,
                () -> compiler.compile(
                        null,
                        null,
                        unsafePage,
                        definition));

        assertEquals(SearchPageableValidationException.SORT_RULES_FORBIDDEN, sortException.code());
        assertEquals(SearchPageableValidationException.PAGE_LIMIT_EXCEEDED, pageException.code());
    }

    @Test
    void validatesSearchDefinitionPathsAgainstJpaMetamodel() {
        jpaDefinitionValidator().validate(standardProductSearch());
    }

    @Test
    void rejectsJavaPropertiesThatAreNotJpaPersistentAttributes() {
        SearchDefinition<Product> definition = SearchDefinition.builder().entity(Product.class)
                .fields(fields -> fields.add("displayName", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        JpaSearchDefinitionValidator validator = jpaDefinitionValidator();

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                () -> validator.validate(definition));

        assertEquals(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED, exception.code());
    }

    private JpaSearchDefinitionValidator jpaDefinitionValidator() {
        return new JpaSearchDefinitionValidator(entityManager.getEntityManager().getEntityManagerFactory());
    }

    @SafeVarargs
    private final Page<Product> search(
            String filter,
            String query,
            Pageable pageable,
            SearchDefinition<Product> definition,
            Specification<Product>... specifications) {
        CompiledSearch<Product> compiled =
                compiler.compile(filter, query, pageable, definition, specifications);
        return products.findAll(compiled.specification(), compiled.pageable());
    }

    private void assertRsqlCode(
            String filter,
            SearchDefinition<Product> definition,
            String expectedCode) {
        PageRequest pageRequest = PageRequest.of(0, 1);
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> compiler.compile(filter, null, pageRequest, definition));

        assertEquals(expectedCode, exception.code());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CatalogCodeSearchConfiguration {
        @Bean
        SearchRsqlEngineCustomizer catalogCodeRsqlCustomizer() {
            ApplicationConversionService conversionService = new ApplicationConversionService();
            conversionService.addConverter(String.class, CatalogCode.class, CatalogCode::parse);
            return builder -> builder
                    .conversionService(conversionService)
                    .operator(RsqlOperatorDescriptor.builder(CATEGORY_CODE)
                            .symbol("=categoryCode=")
                            .argumentType(CatalogCode.class)
                            .build(),
                            context -> {
                                CatalogCode code = (CatalogCode) context.argument(0);
                                return context.criteriaBuilder().equal(context.path(), code.value());
                            });
        }
    }

    private record CatalogCode(String value) implements Comparable<CatalogCode> {
        private static CatalogCode parse(String source) {
            if (!source.startsWith("catalog_")) {
                throw new IllegalArgumentException("catalog code must use catalog_ prefix");
            }
            return new CatalogCode(source.substring("catalog_".length()));
        }

        @Override
        public int compareTo(CatalogCode other) {
            return value.compareTo(other.value);
        }
    }

}
