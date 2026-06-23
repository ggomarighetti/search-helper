package io.github.ggomarighetti.jparsqlsearch.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ggomarighetti.jparsqlsearch.compile.CompiledSearch;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductRepository;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresExplain;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresPlan;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresQueryRecorder;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresQueryRecorder.Capture;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresRecordingConfiguration;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresSchemaInitializer;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresTestEnvironment;
import io.github.ggomarighetti.jparsqlsearch.integration.postgres.ProductPlanSeeder;
import java.util.Set;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductSpecifications.publishedProducts;
import static io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresQueryRecorder.QueryKind.CONTENT;
import static io.github.ggomarighetti.jparsqlsearch.integration.postgres.PostgresQueryRecorder.QueryKind.COUNT;
import static io.github.ggomarighetti.jparsqlsearch.integration.ProductSearchFixtures.standardProductSearch;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        showSql = false,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ProductSearchTestApplication.class)
@Import({
        PostgresRecordingConfiguration.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSearchPostgresPlanIT {
    private static final String PRODUCTS = "catalog_products";

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        PostgresTestEnvironment.register(registry);
    }

    @Autowired
    private ProductRepository products;

    @Autowired
    private SearchCompiler compiler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgresQueryRecorder recorder;

    private SearchDefinition<Product> definition;
    private PostgresExplain explain;

    @BeforeAll
    void preparePlanDataset() {
        PostgresSchemaInitializer.initialize(jdbcTemplate);
        ProductPlanSeeder.seed(jdbcTemplate);
        PostgresSchemaInitializer.createIndexesAndAnalyze(jdbcTemplate);
        definition = standardProductSearch(16);
        explain = new PostgresExplain(dataSource, recorder, new ObjectMapper());
    }

    @Test
    void standardSearchUsesIndexesAndDoesNotSpillToDisk() {
        Capture<Page<Product>> capture = search(
                "categoryCode==CAT-050;supplierCountry==US;price=ge=1000;active==true",
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("price"))));

        assertThat(capture.result().getTotalElements()).isGreaterThan(10);

        PostgresPlan content = explain.explain("standard-search", capture.single(CONTENT), true);
        PostgresPlan count = explain.explain("standard-search", capture.single(COUNT), true);

        assertIndexedSelectivePlan(content);
        assertNoDiskSpill(content);
        assertNoDiskSpill(count);
        assertReasonableEstimate(content);
        assertReasonableEstimate(count);
    }

    @Test
    void toManyFilterKeepsPageResultsUniqueAndExplainsContentAndCount() {
        Capture<Page<Product>> capture = search(
                "reviewRating=ge=4",
                null,
                PageRequest.of(0, 20, Sort.by("sku")));

        Page<Product> page = capture.result();
        assertThat(page.getTotalElements()).isGreaterThan(20);
        assertThat(page.getContent())
                .extracting(Product::getSku)
                .doesNotHaveDuplicates();

        PostgresPlan content = explain.explain("to-many-pagination", capture.single(CONTENT), true);
        PostgresPlan count = explain.explain("to-many-pagination", capture.single(COUNT), true);

        assertNoDiskSpill(content);
        assertNoDiskSpill(count);
        assertReasonableEstimate(content);
        assertReasonableEstimate(count);
    }

    @Test
    void eightMixedOrBranchesProduceAParseableNonExternalPlan() {
        String filter = String.join(",",
                "sku==" + ProductPlanSeeder.sku(10),
                "publicId==" + ProductPlanSeeder.productUuid(11),
                "internalId==12",
                "price==" + ProductPlanSeeder.price(13),
                "stock==14",
                "releasedOn==" + ProductPlanSeeder.releaseDate(15),
                "categoryCode==CAT-050",
                "supplierName==\"Supplier 010\"");

        Capture<Page<Product>> capture = search(filter, null, PageRequest.of(0, 25));
        PostgresPlan plan = explain.explain("or-eight-mixed", capture.single(CONTENT), false);

        assertThat(plan.nodeTypes()).isNotEmpty();
        assertThat(plan.hasExternalSort()).isFalse();
    }

    @Test
    void sixteenSkuOrBranchesUseIndexedAccess() {
        String filter = LongStream.rangeClosed(100, 115)
                .mapToObj(id -> "sku==" + ProductPlanSeeder.sku(id))
                .collect(java.util.stream.Collectors.joining(","));

        Capture<Page<Product>> capture = search(filter, null, PageRequest.of(0, 25));
        PostgresPlan plan = explain.explain("or-sixteen-skus", capture.single(CONTENT), false);

        assertIndexedSelectivePlan(plan);
    }

    @ParameterizedTest(name = "IN with {0} SKU values")
    @ValueSource(ints = {1, 10, 50})
    void skuInUsesIndexedAccess(int size) {
        String arguments = LongStream.rangeClosed(1_000, 999L + size)
                .mapToObj(ProductPlanSeeder::sku)
                .collect(java.util.stream.Collectors.joining(","));

        Capture<Page<Product>> capture =
                search("sku=in=(" + arguments + ")", null, PageRequest.of(0, 50));
        PostgresPlan plan = explain.explain("sku-in-" + size, capture.single(CONTENT), false);

        assertIndexedSelectivePlan(plan);
    }

    @Test
    void sortingByCategoryRelationUsesAnInMemoryPlan() {
        Capture<Page<Product>> capture =
                search(null, null, PageRequest.of(0, 10, Sort.by("categoryCode")));
        PostgresPlan plan = explain.explain("sort-category", capture.single(CONTENT), false);

        assertThat(plan.nodeTypes()).isNotEmpty();
        assertThat(plan.hasExternalSort()).isFalse();
    }

    @Test
    void sortingBySupplierRelationUsesAnInMemoryPlan() {
        Capture<Page<Product>> capture =
                search(null, null, PageRequest.of(0, 10, Sort.by("supplierName")));
        PostgresPlan plan = explain.explain("sort-supplier", capture.single(CONTENT), false);

        assertThat(plan.nodeTypes()).isNotEmpty();
        assertThat(plan.hasExternalSort()).isFalse();
    }

    @Test
    void freeTextSearchUsesTrigramIndexesWithoutDiskSpill() {
        Capture<Page<Product>> capture =
                search(null, "quartzneedle", PageRequest.of(0, 2, Sort.by("sku")));

        assertThat(capture.result().getTotalElements()).isEqualTo(3);

        PostgresPlan content = explain.explain("free-text-trigram", capture.single(CONTENT), true);
        PostgresPlan count = explain.explain("free-text-trigram", capture.single(COUNT), true);

        Set<String> trigramIndexes = Set.of(
                "idx_products_name_trgm",
                "idx_products_sku_trgm",
                "idx_products_description_trgm");
        assertThat(trigramIndexes.stream().anyMatch(content::usesIndex)).isTrue();
        assertThat(content.hasSequentialScan(PRODUCTS)).isFalse();
        assertNoDiskSpill(content);
        assertNoDiskSpill(count);
        assertReasonableEstimate(content);
        assertReasonableEstimate(count);
    }

    @Test
    void heterogeneousOrCountCostsMoreThanHomogeneousIndexedOr() {
        String homogeneous = LongStream.rangeClosed(100, 115)
                .mapToObj(id -> "sku==" + ProductPlanSeeder.sku(id))
                .collect(java.util.stream.Collectors.joining(","));
        String heterogeneous = String.join(",",
                "sku==" + ProductPlanSeeder.sku(10),
                "publicId==" + ProductPlanSeeder.productUuid(11),
                "internalId==12",
                "price==" + ProductPlanSeeder.price(13),
                "stock==14",
                "releasedOn==" + ProductPlanSeeder.releaseDate(15),
                "categoryCode==CAT-050",
                "supplierName==\"Supplier 010\"");

        Capture<Page<Product>> homogeneousCapture =
                search(homogeneous, null, PageRequest.of(0, 10));
        Capture<Page<Product>> heterogeneousCapture =
                search(heterogeneous, null, PageRequest.of(0, 10));

        PostgresPlan homogeneousCount =
                explain.explain("a04-or-homogeneous-count", homogeneousCapture.single(COUNT), true);
        PostgresPlan heterogeneousCount =
                explain.explain("a04-or-heterogeneous-count", heterogeneousCapture.single(COUNT), true);

        assertThat(heterogeneousCount.totalCost()).isGreaterThan(homogeneousCount.totalCost());
        assertThat(heterogeneousCount.sharedHitBlocks()).isGreaterThan(homogeneousCount.sharedHitBlocks());
        assertThat(heterogeneousCount.hasSequentialScan(PRODUCTS)).isTrue();
    }

    @Test
    void toManyCountDominatesLimitedContentPlan() {
        Capture<Page<Product>> capture =
                search("reviewRating=ge=1", null, PageRequest.of(0, 20, Sort.by("sku")));

        PostgresPlan content =
                explain.explain("a04-to-many-content", capture.single(CONTENT), true);
        PostgresPlan count =
                explain.explain("a04-to-many-count", capture.single(COUNT), true);

        assertThat(count.totalCost()).isGreaterThan(content.totalCost() * 10);
        assertThat(count.sharedHitBlocks()).isGreaterThan(content.sharedHitBlocks());
        assertThat(count.executionTimeMillis()).isPositive();
    }

    @Test
    void relationSortCostGrowsWithOffset() {
        Capture<Page<Product>> firstPage =
                search(null, null, PageRequest.of(0, 50, Sort.by("supplierName")));
        Capture<Page<Product>> deepPage =
                search(null, null, PageRequest.of(20, 50, Sort.by("supplierName")));

        PostgresPlan firstPlan =
                explain.explain("a04-relation-sort-offset-0", firstPage.single(CONTENT), true);
        PostgresPlan deepPlan =
                explain.explain("a04-relation-sort-offset-1000", deepPage.single(CONTENT), true);

        assertThat(deepPlan.totalCost()).isGreaterThan(firstPlan.totalCost() * 5);
        assertThat(deepPlan.sharedHitBlocks()).isGreaterThan(firstPlan.sharedHitBlocks());
    }

    private Capture<Page<Product>> search(String filter, String query, PageRequest pageable) {
        CompiledSearch<Product> compiled =
                compiler.compile(filter, query, pageable, definition, publishedProducts());
        return recorder.capture(() -> products.findAll(compiled.specification(), compiled.pageable()));
    }

    private static void assertIndexedSelectivePlan(PostgresPlan plan) {
        assertThat(plan.usesAnyIndex()).isTrue();
        assertThat(plan.hasSequentialScan(PRODUCTS)).isFalse();
        assertThat(plan.hasExternalSort()).isFalse();
    }

    private static void assertNoDiskSpill(PostgresPlan plan) {
        assertThat(plan.hasExternalSort()).isFalse();
        assertThat(plan.tempReadBlocks()).isZero();
        assertThat(plan.tempWrittenBlocks()).isZero();
    }

    private static void assertReasonableEstimate(PostgresPlan plan) {
        assertThat(plan.rootEstimateRatio()).isLessThanOrEqualTo(20.0);
    }
}
