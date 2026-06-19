package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.query.SearchQuery;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.validator.cfg.defs.MaxDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.junit.jupiter.api.Test;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

class SearchDefinitionTest {
    @Test
    void storesExplicitStringFieldType() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertEquals(String.class, definition.field("email").orElseThrow().type());
        assertTrue(definition.fields().containsKey("email"));
    }

    @Test
    void declaresExplicitFieldType() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("price", BigDecimal.class))
                .build();

        assertEquals(BigDecimal.class, definition.field("price").orElseThrow().type());
    }

    @Test
    void validatesDefaultFieldPathAgainstEntityPath() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", BigDecimal.class)
                        .path("price")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        assertEquals("price", definition.field("amount").orElseThrow().path().orElseThrow());
        assertEquals("price", definition.filteringPaths().get("amount"));
    }

    @Test
    void rejectsMalformedFieldFilteringAndSortingPaths() {
        assertThrows(IllegalArgumentException.class, SearchDefinitionTest::buildFieldWithMalformedDefaultPath);
        assertThrows(IllegalArgumentException.class, SearchDefinitionTest::buildFieldWithMalformedFilteringPath);
        assertThrows(IllegalArgumentException.class, SearchDefinitionTest::buildFieldWithMalformedSortingPath);
    }

    @Test
    void validatesFieldPathAgainstDeclaredEntitySubtype() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("expiresAt", java.time.Instant.class)
                        .subtype(TestTypes.PerishableProduct.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        SearchField<?> field = definition.field("expiresAt").orElseThrow();

        assertEquals(TestTypes.PerishableProduct.class, field.subtype().orElseThrow());
        assertEquals("expiresAt", definition.filteringPaths().get("expiresAt"));
    }

    @Test
    void rejectsSubtypeOutsideEntityHierarchy() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class);

        IllegalArgumentException exception = thrownBy(
                IllegalArgumentException.class,
                () -> addInvalidSubtypeField(builder));

        assertTrue(exception.getMessage().contains("must extend entity"));
    }

    @Test
    void validatesFilteringPathOverrideAgainstEntityPath() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("taxId", String.class)
                        .filterable(filter -> filter
                                .path("owner.taxIdentifier")
                                .allow(EQUAL)))
                .build();

        assertEquals(String.class, definition.field("taxId").orElseThrow().type());
        assertEquals("owner.taxIdentifier", definition.filteringPaths().get("taxId"));
    }

    @Test
    void allowsSortingPathOverrideToDifferFromFilteringPath() {
        SearchField<?> field = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("customerName", String.class)
                        .filterable(filter -> filter
                                .path("customer.name")
                                .allow(EQUAL))
                        .sortable(sort -> sort.path("customer.sortName")))
                .build()
                .field("customerName")
                .orElseThrow();

        assertEquals("customer.name", field.filtering().path());
        assertEquals("customer.sortName", field.sorting().path());
    }

    @Test
    void enablesBothSortingDirectionsByDefault() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class).sortable())
                .build();

        SearchField<?> field = definition.field("email").orElseThrow();

        assertTrue(field.sorting().enabled());
        assertTrue(field.sorting().directions().contains(ASC));
        assertTrue(field.sorting().directions().contains(DESC));
        assertEquals("email", definition.sortingPaths().get("email"));
    }

    @Test
    void searchableEnablesDefaultFilteringAndSorting() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class).searchable())
                .build();

        SearchField<?> field = definition.field("email").orElseThrow();

        assertTrue(field.filtering().enabled());
        assertTrue(field.filtering().allows(EQUAL));
        assertTrue(field.sorting().enabled());
        assertEquals(Set.of(ASC, DESC), field.sorting().directions());
    }

    @Test
    void restrictsSortingDirectionsWhenDeclared() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .sortable(sort -> sort.allow(DESC)))
                .build();

        SearchField<?> field = definition.field("email").orElseThrow();

        assertFalse(field.sorting().accepts(ASC));
        assertTrue(field.sorting().accepts(DESC));
        assertEquals(Set.of(DESC), definition.sortingDirections().get("email"));
    }

    @Test
    void materializesDefinitionLimitCustomizerAtBuildTime() {
        AtomicInteger invocations = new AtomicInteger();
        SearchPolicy basePolicy = SearchPolicy.builder()
                .paging(paging -> paging.maxSize(10))
                .build();
        SearchPolicy runtimePolicy = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentsPerComparison(10))
                .paging(paging -> paging.maxSize(3))
                .build();

        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(basePolicy)
                .entity(TestTypes.Product.class)
                .limits(limits -> {
                    invocations.incrementAndGet();
                    limits.paging(paging -> paging.maxSize(7));
                })
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertEquals(1, invocations.get());
        SearchPolicy effectiveLimits = definition.effectiveLimits(runtimePolicy);
        assertEquals(7, effectiveLimits.paging().maxSize());
        assertEquals(10, effectiveLimits.filter().maxArgumentsPerComparison());
        assertEquals(1, invocations.get());
    }

    @Test
    void preservesExplicitOverrideEvenWhenItMatchesBuilderBaseValue() {
        SearchPolicy runtimePolicy = SearchPolicy.builder()
                .paging(paging -> paging.maxSize(5))
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging.maxSize(100)))
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertEquals(100, definition.effectiveLimits(runtimePolicy).paging().maxSize());
    }

    @Test
    void capturesEveryPolicyBuilderSetterInPartialOverride() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(SearchDefinitionTest::customizeEveryLimit)
                .fields(fields -> fields.add("email", String.class))
                .build();
        SearchPolicy expected = customizeEveryLimit(SearchPolicy.builder()).build();

        assertPolicyEquals(expected, definition.effectiveLimits(SearchPolicy.defaults()));
    }

    @Test
    void explicitPolicyStillReplacesGlobalPolicyCompletely() {
        SearchPolicy explicit = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentsPerComparison(17))
                .paging(paging -> paging.maxSize(19))
                .build();
        SearchPolicy global = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentsPerComparison(3))
                .paging(paging -> paging.maxSize(5))
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(explicit)
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertSame(explicit, definition.effectiveLimits(global));
    }

    @Test
    void fallsBackToGlobalPolicyWhenNoDefinitionLimitsAreDeclared() {
        SearchPolicy global = SearchPolicy.builder()
                .paging(paging -> paging.maxSize(17))
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertSame(global, definition.effectiveLimits(global));
        assertTrue(definition.limits().isEmpty());
        assertTrue(SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(global)
                .fields(fields -> fields.add("email", String.class))
                .build()
                .limits()
                .isPresent());
    }

    @Test
    void rejectsDuplicatePagingAndLimitsDeclarations() {
        var pagingBuilder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .paging();
        var explicitLimitsBuilder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(SearchPolicy.defaults());
        var customLimitsBuilder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging.maxSize(10)));

        assertNotNull(thrownBy(IllegalArgumentException.class, pagingBuilder::paging));
        thrownBy(
                IllegalArgumentException.class,
                () -> explicitLimitsBuilder.limits(limits -> limits.paging(paging -> paging.maxSize(10))));
        thrownBy(IllegalArgumentException.class, () -> customLimitsBuilder.limits(SearchPolicy.defaults()));
    }

    @Test
    void rejectsDuplicateFilteringAndSortingDeclarations() {
        assertNotNull(thrownBy(IllegalArgumentException.class, () ->
                SearchDefinition.builder()
                        .entity(TestTypes.Product.class)
                        .fields(fields -> fields.add("email", String.class)
                                .filterable()
                                .filterable())));
        thrownBy(IllegalArgumentException.class, () ->
                SearchDefinition.builder()
                        .entity(TestTypes.Product.class)
                        .fields(fields -> fields.add("email", String.class)
                                .sortable()
                                .sortable()));
    }

    @Test
    void fieldsCustomizerOverloadReturnsTheSameDslAfterApplyingCustomizer() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class, SearchField.Builder::filterable)
                        .add("price", BigDecimal.class))
                .build();

        assertTrue(definition.field("email").orElseThrow().filtering().enabled());
        assertEquals(BigDecimal.class, definition.field("price").orElseThrow().type());
    }

    @Test
    void rejectsDuplicateSelectors() {
        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, () ->
                SearchDefinition.builder()
                        .entity(TestTypes.Product.class)
                        .fields(fields -> {
                            fields.add("email", String.class);
                            fields.add("email", String.class);
                        }));

        assertEquals("selector 'email' is already declared", exception.getMessage());
    }

    @Test
    void leavesFieldWithoutSortingAsNotSortable() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        SearchField<?> field = definition.field("email").orElseThrow();

        assertFalse(field.sorting().enabled());
        assertTrue(definition.sortingPaths().isEmpty());
    }

    private static SearchPolicy.Builder customizeEveryLimit(SearchPolicy.Builder limits) {
        return limits
                .rsql(rsql -> rsql
                        .maxLength(1_000)
                        .maxParenthesesDepth(4)
                        .maxNodes(30)
                        .maxDepth(5)
                        .maxLogicalChildren(10))
                .filter(filter -> filter
                        .maxComparisons(20)
                        .maxComparisonsPerSelector(7)
                        .maxArgumentsPerComparison(15)
                        .maxArgumentsTotal(40)
                        .maxArgumentLength(120)
                        .maxInValues(14)
                        .maxNotInValues(12)
                        .maxBetweenRanges(8)
                        .maxNegatedComparisons(4)
                        .maxOrBranches(9)
                        .maxOrSelectors(6)
                        .maxOrJoinRoots(1)
                        .maxHeterogeneousOrBranches(5)
                        .maxJoinedPaths(3)
                        .maxToManyPaths(2)
                        .allowToManyFiltering(false)
                        .requireDistinctForToMany(false)
                        .like(like -> like
                                .maxPatternLength(80)
                                .minLiteralLength(2)
                                .allowLeadingWildcard(true)
                                .allowTrailingWildcard(false)
                                .allowContains(true)
                                .maxWildcards(3)
                                .allowIgnoreCase(false)))
                .paging(paging -> paging
                        .minPage(1)
                        .maxPage(20)
                        .minSize(2)
                        .maxSize(50)
                        .maxOffset(500)
                        .allowUnpaged(true)
                        .unpagedSize(30)
                        .maxUnpagedSize(60)
                        .page(page -> page
                                .allowToManyCount(false)
                                .maxToManyPaths(2)
                                .allowDistinctCount(false)
                                .maxJoinedPaths(3))
                        .slice(slice -> slice
                                .enabled(false)
                                .preferForToMany(false)
                                .maxSize(40)))
                .sorting(sorting -> sorting
                        .maxOrders(5)
                        .allowRelationSorting(false)
                        .maxRelationOrders(2)
                        .allowIgnoreCase(true)
                        .allowNullHandling(true)
                        .maxJoinedPaths(3)
                        .disallowToManySorting(false))
                .query(query -> query
                        .enabled(false)
                        .minLength(2)
                        .maxLength(40)
                        .requireValidator(true)
                        .allowWithToManyFilter(false)
                        .allowWithRelationSort(false)
                        .allowWithUnpaged(false))
                .paths(paths -> paths.maxDepth(4));
    }

    private static void assertPolicyEquals(SearchPolicy expected, SearchPolicy actual) {
        assertEquals(expected.rsql(), actual.rsql());
        assertEquals(expected.filter(), actual.filter());
        assertEquals(expected.paging(), actual.paging());
        assertEquals(expected.sorting(), actual.sorting());
        assertEquals(expected.query(), actual.query());
        assertEquals(expected.paths(), actual.paths());
    }

    @Test
    void storesPagingAsGlobalSearchDefinitionConfiguration() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .paging(paging -> paging.size(size -> {
                    size.rule(new MaxDef().value(100));
                }))
                .build();

        assertTrue(definition.paging().enabled());
        assertTrue(definition.paging().accepts(0, 100));
        assertFalse(definition.paging().accepts(0, 101));
    }

    @Test
    void leavesPagingDisabledByDefault() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertFalse(definition.paging().enabled());
    }

    @Test
    void leavesQueryDisabledByDefault() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        assertFalse(definition.query().enabled());
    }

    @Test
    void storesInlineQueryConfiguration() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .query(query -> query.specification(term -> (root, criteria, builder) -> builder.conjunction()))
                .build();

        assertTrue(definition.query().enabled());
    }

    @Test
    void storesReusableQueryConfiguration() {
        SearchQuery<TestTypes.Product> query = SearchQuery.<TestTypes.Product>builder()
                .specification(term -> (root, criteria, builder) -> builder.conjunction())
                .build();

        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .query(query)
                .build();

        assertSame(query, definition.query());
    }

    @Test
    void remainsReusableWithoutLifecycleManagement() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL, operator -> operator
                                .each(each -> each.rule(new SizeDef().min(3))))))
                .query(query -> query
                        .rule(new SizeDef().min(3))
                        .specification(term -> (root, criteria, builder) -> builder.conjunction()))
                .paging(paging -> paging.size(size -> size.rule(new MaxDef().value(100))))
                .build();

        assertEquals("email", definition.filteringPaths().get("email"));
        assertTrue(definition.query().accepts("abc"));
        assertTrue(definition.paging().accepts(0, 100));
        assertFalse(definition.paging().accepts(0, 101));
    }

    @Test
    void closesRuleValidatorsIdempotentlyForDynamicDefinitions() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL, operator -> operator
                                .each(each -> each.rule(new SizeDef().min(3))))))
                .query(query -> query
                        .rule(new SizeDef().min(3))
                        .specification(term -> (root, criteria, builder) -> builder.conjunction()))
                .paging(paging -> paging.size(size -> size.rule(new MaxDef().value(100))))
                .build();

        assertDoesNotThrow(definition::close);
        assertDoesNotThrow(definition::close);
    }

    @Test
    void rejectsDuplicatedQueryDeclaration() {
        SearchQuery<TestTypes.Product> query = SearchQuery.<TestTypes.Product>builder()
                .specification(term -> (root, criteria, builder) -> builder.conjunction())
                .build();
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .query(definitionQuery -> definitionQuery.specification(term ->
                        (root, criteria, criteriaBuilder) -> criteriaBuilder.conjunction()));

        IllegalArgumentException exception = thrownBy(
                IllegalArgumentException.class,
                () -> declareDuplicateQuery(builder, query));

        assertEquals("query is already declared", exception.getMessage());
    }

    @Test
    void storesEntityTypeForPathAndJoinMetadata() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", BigDecimal.class)
                        .path("price")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        assertEquals(TestTypes.Product.class, definition.entity());
    }

    @Test
    void derivesRelationAndToManyTopologyAutomatically() {
        SearchDefinition<Product> definition = SearchDefinition.builder()
                .entity(Product.class)
                .fields(fields -> {
                    fields.add("categoryName", String.class)
                            .path("category.name")
                            .sortable();
                    fields.add("reviewRating", Integer.class)
                            .path("reviews.rating")
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();

        SearchField<?> category = definition.field("categoryName").orElseThrow();
        SearchField<?> review = definition.field("reviewRating").orElseThrow();

        assertEquals(Set.of("category"), category.sorting().topology().joinedPaths());
        assertEquals(Set.of("reviews"), review.filtering().topology().joinedPaths());
        assertEquals(Set.of("reviews"), review.filtering().topology().toManyPaths());
    }

    @Test
    void doesNotTreatPlainNestedPojoPathAsJpaJoin() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("customerName", String.class)
                        .path("customer.name")
                        .sortable())
                .build();

        SearchField<?> customer = definition.field("customerName").orElseThrow();

        assertEquals(Set.of(), customer.sorting().topology().joinedPaths());
        assertEquals(Set.of(), customer.sorting().topology().toManyPaths());
    }

    @Test
    void returnsUnmodifiableFilteringOperators() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL, IN)))
                .build();

        Set<?> operators = definition.filteringOperators();

        assertEquals(Set.of(EQUAL, IN), operators);
        thrownBy(UnsupportedOperationException.class, operators::clear);
    }

    @Test
    void leavesFieldWithoutFilteringAsNotFilterable() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        SearchField<?> field = definition.field("email").orElseThrow();
        assertFalse(field.filtering().enabled());
        assertTrue(definition.filteringPaths().isEmpty());
    }

    @Test
    void rejectsFieldTypeThatDoesNotMatchEntityPath() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", String.class)
                        .path("price")
                        .filterable(filter -> filter.allow(EQUAL)));

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertEquals(
                "selector 'amount' path 'price' resolves to type 'java.math.BigDecimal' but was declared as 'java.lang.String'",
                exception.getMessage());
    }

    @Test
    void rejectsFilteringWithoutAllowedOperators() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("taxId", String.class)
                        .path("person.taxIdentifier")
                        .filterable(filter -> {}));

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertEquals("selector 'taxId' filtering must declare at least one allowed operator", exception.getMessage());
    }

    @Test
    void rejectsPathDeeperThanSafetyLimitByDefault() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("countryCode", String.class)
                        .path("customer.region.country.code")
                        .sortable());

        SearchDefinitionValidationException exception =
                thrownBy(SearchDefinitionValidationException.class, builder::build);

        assertEquals(SearchDefinitionValidationException.PATH_LIMIT_EXCEEDED, exception.code());
    }

    @Test
    void allowsDeeperPathWhenDefinitionOverridesLimit() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.paths(paths -> paths.maxDepth(4)))
                .fields(fields -> fields.add("countryCode", String.class)
                        .path("customer.region.country.code")
                        .sortable())
                .build();

        assertEquals("customer.region.country.code", definition.sortingPaths().get("countryCode"));
    }

    @Test
    void resolvesNestedCollectionValuedPath() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        assertEquals("reviews.rating", definition.filteringPaths().get("reviewRating"));
    }

    @Test
    void rejectsNestedCollectionValuedPathWhenElementTypeCannotBeResolved() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("rawReviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)));

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertTrue(exception.getMessage().contains("could not be resolved"));
    }

    @Test
    void rejectsSortingThroughCollectionValuedPath() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .sortable());

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertTrue(exception.getMessage().contains("must not traverse collection-valued paths"));
    }

    @Test
    void rejectsSortingOnTerminalCollectionValuedPath() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("tags", java.util.List.class)
                        .path("tags")
                        .sortable());

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertTrue(exception.getMessage().contains("must not traverse collection-valued paths"));
    }

    @Test
    void rejectsIgnoreCaseSortingForNonTextPaths() {
        var builder = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", BigDecimal.class)
                        .path("price")
                        .sortable(SearchSorting.Builder::allowIgnoreCase));

        IllegalArgumentException exception = thrownBy(IllegalArgumentException.class, builder::build);

        assertTrue(exception.getMessage().contains("ignoreCase sorting requires a CharSequence path"));
    }

    private static void addInvalidSubtypeField(SearchDefinition.Builder<TestTypes.Product> builder) {
        builder.fields(fields -> fields.add("expiresAt", java.time.Instant.class)
                .subtype(String.class)
                .filterable(filter -> filter.allow(EQUAL)));
    }

    private static void buildFieldWithMalformedDefaultPath() {
        SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .path("email.")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
    }

    private static void buildFieldWithMalformedFilteringPath() {
        SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter
                                .path(".email")
                                .allow(EQUAL)))
                .build();
    }

    private static void buildFieldWithMalformedSortingPath() {
        SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .sortable(sort -> sort.path("email.")))
                .build();
    }

    private static void declareDuplicateQuery(
            SearchDefinition.Builder<TestTypes.Product> builder,
            SearchQuery<TestTypes.Product> query) {
        builder.query(query);
    }
}
