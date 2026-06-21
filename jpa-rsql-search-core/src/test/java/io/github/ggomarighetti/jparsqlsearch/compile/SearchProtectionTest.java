package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.unit.TestRsqlEngines;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.query.validation.SearchQueryValidationException;
import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.BETWEEN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE_NOT_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_BETWEEN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_NULL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchProtectionTest {
    @Test
    void rejectsShortToManyFilterByExplicitTopologyRule() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxToManyPaths(0))
                .build();
        RsqlSearchGuard guard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
                        .conversionService(
                                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> guard.specification("reviewRating==5", definition));

        assertRule(exception, "filter.max-to-many-paths", 1, 0);
    }

    @Test
    void rejectsInValuesAboveConfiguredLimit() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxInValues(1))
                .build();
        RsqlSearchGuard guard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
                        .conversionService(
                                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("taxId", String.class)
                        .path("owner.taxIdentifier")
                        .filterable(filter -> filter.allow(IN)))
                .build();

        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> guard.specification("taxId=in=(1,2)", definition));

        assertRule(exception, "filter.max-in-values", 2, 1);
    }

    @Test
    void rejectsOrAcrossTooManyRelationRoots() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxOrJoinRoots(1))
                .build();
        RsqlSearchGuard guard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
                        .conversionService(
                                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
        SearchDefinition<Product> definition = SearchDefinition.builder(policy)
                .entity(Product.class)
                .fields(fields -> {
                    fields.add("categoryCode", String.class)
                            .path("category.code")
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("supplierName", String.class)
                            .path("supplier.name")
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();

        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> guard.specification(
                        "categoryCode==LAPTOP,supplierName==Acme",
                        definition));

        assertRule(exception, "filter.max-or-join-roots", 2, 1);
    }

    @Test
    void likeProtectionDoesNotTreatEscapedTrailingWildcardAsPatternWildcard() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text.allowTrailingWildcard(false)))
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class)
                        .filterable(filter -> filter.allow(LIKE)))
                .build();
        RsqlOperatorDescriptor descriptor = DefaultRsqlOperatorDescriptors.all().stream()
                .filter(candidate -> LIKE.equals(candidate.operator()))
                .findFirst()
                .orElseThrow();

        SearchProtectionContext protection =
                new SearchProtectionContext(policy, SearchProtectionContext.Mode.PAGE);
        var nameField = definition.field("name").orElseThrow();
        List<String> escapedArguments = List.of("abc\\*");
        assertDoesNotThrow(() -> protection.recordComparison(
                nameField,
                descriptor,
                1,
                escapedArguments));

        SearchProtectionContext wildcardProtection =
                new SearchProtectionContext(policy, SearchProtectionContext.Mode.PAGE);
        List<String> wildcardArguments = List.of("abc*");
        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> wildcardProtection.recordComparison(
                        nameField,
                        descriptor,
                        1,
                        wildcardArguments));

        assertRule(exception, "filter.text.allow-trailing-wildcard", 1, 0);
    }

    @Test
    void rejectsOperatorSpecificComparisonLimits() {
        SearchDefinition<TestTypes.Product> definition = comparisonDefinition(SearchPolicy.defaults());

        SearchProtectionException notIn = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(
                        SearchPolicy.builder().filter(filter -> filter.maxNotInValues(1)).build(),
                        SearchProtectionContext.Mode.PAGE)
                        .recordComparison(
                                definition.field("amount").orElseThrow(),
                                descriptor(NOT_IN),
                                2,
                                List.of("10", "20")));
        SearchProtectionException between = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(
                        SearchPolicy.builder().filter(filter -> filter.maxBetweenRanges(0)).build(),
                        SearchProtectionContext.Mode.PAGE)
                        .recordComparison(
                                definition.field("amount").orElseThrow(),
                                descriptor(BETWEEN),
                                2,
                                List.of("10", "20")));
        SearchProtectionException negated = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(
                        SearchPolicy.builder().filter(filter -> filter.maxNegatedComparisons(0)).build(),
                        SearchProtectionContext.Mode.PAGE)
                        .recordComparison(
                                definition.field("amount").orElseThrow(),
                                descriptor(NOT_EQUAL),
                                1,
                                List.of("10")));
        SearchProtectionException negatedBetween = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(
                        SearchPolicy.builder().filter(filter -> filter.maxNegatedComparisons(0)).build(),
                        SearchProtectionContext.Mode.PAGE)
                        .recordComparison(
                                definition.field("amount").orElseThrow(),
                                descriptor(NOT_BETWEEN),
                                2,
                                List.of("10", "20")));
        SearchProtectionException negatedNull = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(
                        SearchPolicy.builder().filter(filter -> filter.maxNegatedComparisons(0)).build(),
                        SearchProtectionContext.Mode.PAGE)
                        .recordComparison(
                                definition.field("amount").orElseThrow(),
                                descriptor(NOT_NULL),
                                0,
                                List.of()));

        assertRule(notIn, "filter.max-not-in-values", 2, 1);
        assertRule(between, "filter.max-between-ranges", 1, 0);
        assertRule(negated, "filter.max-negated-comparisons", 1, 0);
        assertRule(negatedBetween, "filter.max-negated-comparisons", 1, 0);
        assertRule(negatedNull, "filter.max-negated-comparisons", 1, 0);
    }

    @Test
    void rejectsTextPatternPolicyViolations() {
        SearchDefinition<TestTypes.Product> definition = comparisonDefinition(SearchPolicy.defaults());
        var nameField = definition.field("name").orElseThrow();

        SearchProtectionException ignoreCase = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text.allowIgnoreCase(false)))
                        .build())
                        .recordComparison(nameField, descriptor(IGNORE_CASE_LIKE), 1, List.of("abc")));
        SearchProtectionException length = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text.maxPatternLength(2)))
                        .build())
                        .recordComparison(nameField, descriptor(LIKE), 1, List.of("abc")));
        SearchProtectionException wildcards = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text.maxWildcards(0)))
                        .build())
                        .recordComparison(nameField, descriptor(LIKE), 1, List.of("a*b")));
        SearchProtectionException leading = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text.allowLeadingWildcard(false)))
                        .build())
                        .recordComparison(nameField, descriptor(LIKE), 1, List.of("*abc")));
        SearchProtectionException contains = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text
                                .allowLeadingWildcard(true)
                                .allowTrailingWildcard(true)
                                .allowContains(false)
                                .maxWildcards(2)))
                        .build())
                        .recordComparison(nameField, descriptor(LIKE), 1, List.of("*abc*")));
        SearchProtectionException literal = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.text(text -> text.minLiteralLength(3)))
                        .build())
                        .recordComparison(nameField, descriptor(LIKE), 1, List.of("a*")));

        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text.minLiteralLength(0)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text
                        .minLiteralLength(0)
                        .allowLeadingWildcard(true)
                        .allowTrailingWildcard(true)
                        .allowContains(true)))
                .build())
                .recordComparison(nameField, descriptor(NOT_LIKE), 1, List.of("abc")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text.minLiteralLength(0)))
                .build())
                .recordComparison(nameField, descriptor(IGNORE_CASE), 1, List.of("abc")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text
                        .minLiteralLength(0)
                        .allowLeadingWildcard(true)
                        .allowTrailingWildcard(true)
                        .allowContains(true)))
                .build())
                .recordComparison(nameField, descriptor(IGNORE_CASE_NOT_LIKE), 1, List.of("\\*abc\\*")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text
                        .minLiteralLength(0)
                        .maxWildcards(2)
                        .allowLeadingWildcard(true)
                        .allowTrailingWildcard(true)
                        .allowContains(true)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("%abc%")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text
                        .minLiteralLength(0)
                        .maxWildcards(2)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("a%b")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text
                        .minLiteralLength(0)
                        .allowLeadingWildcard(true)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("*abc")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text.minLiteralLength(0)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("abc*")));
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .filter(filter -> filter.text(text -> text.minLiteralLength(0)))
                .build())
                .recordComparison(nameField, descriptor(LIKE), 1, List.of("\\%abc\\%")));
        assertRule(ignoreCase, "filter.text.allow-ignore-case", 1, 0);
        assertRule(length, "filter.text.max-pattern-length", 3, 2);
        assertRule(wildcards, "filter.text.max-wildcards", 1, 0);
        assertRule(leading, "filter.text.allow-leading-wildcard", 1, 0);
        assertRule(contains, "filter.text.allow-contains", 1, 0);
        assertRule(literal, "filter.text.min-literal-length", 1, 3);
    }

    @Test
    void rejectsDisabledToManyFilteringAndSorting() {
        SearchPolicy filteringPolicy = SearchPolicy.builder()
                .filter(filter -> filter.allowToManyFiltering(false))
                .build();
        SearchDefinition<TestTypes.Product> filteringDefinition = comparisonDefinition(filteringPolicy);
        SearchProtectionException filtering = thrownBy(
                SearchProtectionException.class,
                () -> context(filteringPolicy).recordComparison(
                        filteringDefinition.field("reviewRating").orElseThrow(),
                        descriptor(EQUAL),
                        1,
                        List.of("5")));
        SearchPolicy sortingPolicy = SearchPolicy.builder()
                .sorting(sorting -> sorting.disallowToManySorting(true))
                .build();
        SearchProtectionException sorting = thrownBy(
                SearchProtectionException.class,
                () -> context(sortingPolicy).recordSort(
                        sortingWithToManyTopology(),
                        Sort.Order.asc("tags")));

        assertRule(filtering, "filter.allow-to-many-filtering", 1, 0);
        assertRule(sorting, "sorting.disallow-to-many-sorting", 1, 0);
    }

    @Test
    void allowsInverseProtectionBranchCombinations() {
        SearchDefinition<TestTypes.Product> defaultDefinition = comparisonDefinition(SearchPolicy.defaults());
        SearchProtectionContext distinctNotRequired = context(SearchPolicy.builder()
                .filter(filter -> filter.requireDistinctForToMany(false))
                .build());
        distinctNotRequired.recordComparison(
                defaultDefinition.field("reviewRating").orElseThrow(),
                descriptor(EQUAL),
                1,
                List.of("5"));
        assertDoesNotThrow(distinctNotRequired::completeFilter);
        assertDoesNotThrow(() -> context(SearchPolicy.builder()
                .sorting(sorting -> sorting.disallowToManySorting(false))
                .build())
                .recordSort(sortingWithToManyTopology(), Sort.Order.asc("tags")));

        SearchPolicy toManyFilteringDisabled = SearchPolicy.builder()
                .filter(filter -> filter.allowToManyFiltering(false))
                .build();
        SearchProtectionContext scalarFiltering = context(toManyFilteringDisabled);
        scalarFiltering.recordComparison(
                comparisonDefinition(toManyFilteringDisabled).field("amount").orElseThrow(),
                descriptor(EQUAL),
                1,
                List.of("10"));
        assertDoesNotThrow(scalarFiltering::completeFilter);

        SearchProtectionContext countWithoutToMany = context(SearchPolicy.builder()
                .paging(paging -> paging.page(page -> page.allowToManyCount(false)))
                .build());
        countWithoutToMany.recordPaging(PageRequest.of(0, 10));
        assertDoesNotThrow(countWithoutToMany::completeRequest);
        SearchProtectionContext countWithoutDistinct = context(SearchPolicy.builder()
                .paging(paging -> paging.page(page -> page.allowDistinctCount(false)))
                .build());
        countWithoutDistinct.recordPaging(PageRequest.of(0, 10));
        assertDoesNotThrow(countWithoutDistinct::completeRequest);

        SearchProtectionContext queryWithoutToMany = context(SearchPolicy.builder()
                .query(query -> query.allowWithToManyFilter(false))
                .build());
        queryWithoutToMany.recordQuery("tablet");
        assertDoesNotThrow(queryWithoutToMany::completeRequest);
        SearchProtectionContext queryWithoutRelationSort = context(SearchPolicy.builder()
                .query(query -> query.allowWithRelationSort(false))
                .build());
        queryWithoutRelationSort.recordQuery("tablet");
        queryWithoutRelationSort.recordSort(
                defaultDefinition.field("amount").orElseThrow().sorting(),
                Sort.Order.asc("amount"));
        assertDoesNotThrow(queryWithoutRelationSort::completeRequest);
        SearchProtectionContext queryWithoutUnpaged = context(SearchPolicy.builder()
                .query(query -> query.allowWithUnpaged(false))
                .build());
        queryWithoutUnpaged.recordQuery("tablet");
        queryWithoutUnpaged.recordPaging(PageRequest.of(0, 10));
        assertDoesNotThrow(queryWithoutUnpaged::completeRequest);
    }

    @Test
    void recordsRequestStateAndRejectsCrossComponentLimits() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.requireDistinctForToMany(true))
                .build();
        SearchDefinition<TestTypes.Product> definition = comparisonDefinition(policy);
        SearchProtectionContext protection =
                new SearchProtectionContext(policy, SearchProtectionContext.Mode.PAGE);

        assertEquals(policy, protection.policy());
        assertEquals(SearchProtectionContext.Mode.PAGE, protection.mode());
        protection.recordComparison(
                definition.field("reviewRating").orElseThrow(),
                descriptor(EQUAL),
                1,
                List.of("5"));

        assertEquals(1, protection.joinedPaths());
        assertEquals(1, protection.toManyPaths());
        assertEquals(false, protection.distinct());

        SearchProtectionException exception = thrownBy(SearchProtectionException.class, protection::completeFilter);

        assertRule(exception, "filter.require-distinct-for-to-many", 0, 1);
        protection.recordDistinct();
        assertEquals(true, protection.distinct());
        assertDoesNotThrow(protection::completeFilter);
    }

    @Test
    void rejectsOrPageSliceSortAndQueryCombinations() {
        SearchProtectionException heterogeneousOr = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .filter(filter -> filter.maxHeterogeneousOrBranches(1))
                        .build())
                        .recordOr(2, 2, 0));
        SearchProtectionException sliceDisabled = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(SearchPolicy.builder()
                        .paging(paging -> paging.slice(slice -> slice.enabled(false)))
                        .build(), SearchProtectionContext.Mode.SLICE)
                        .recordPaging(PageRequest.of(0, 10)));
        SearchProtectionException sliceSize = thrownBy(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(SearchPolicy.builder()
                        .paging(paging -> paging.slice(slice -> slice.maxSize(5)))
                        .build(), SearchProtectionContext.Mode.SLICE)
                        .recordPaging(PageRequest.of(0, 10)));
        SearchProtectionException distinctCount = thrownBy(
                SearchProtectionException.class,
                () -> {
                    SearchProtectionContext protection = context(SearchPolicy.builder()
                            .paging(paging -> paging.page(page -> page.allowDistinctCount(false)))
                            .build());
                    protection.recordPaging(PageRequest.of(0, 10));
                    protection.recordDistinct();
                    protection.completeRequest();
                });

        assertRule(heterogeneousOr, "filter.max-heterogeneous-or-branches", 2, 1);
        assertRule(sliceDisabled, "paging.slice.enabled", 1, 0);
        assertRule(sliceSize, "paging.slice.max-size", 10, 5);
        assertRule(distinctCount, "paging.page.allow-distinct-count", 1, 0);
    }

    @Test
    void rejectsSortAndQueryInteractionLimits() {
        SearchDefinition<TestTypes.Product> comparisonDefinition = comparisonDefinition(SearchPolicy.defaults());
        SearchDefinition<Product> sortDefinition = SearchDefinition.builder()
                .entity(Product.class)
                .fields(fields -> fields.add("supplierName", String.class)
                        .path("supplier.name")
                        .sortable())
                .build();
        SearchProtectionException ignoreCase = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .sorting(sorting -> sorting.allowIgnoreCase(false))
                        .build())
                        .recordSort(
                                comparisonDefinition.field("name").orElseThrow().sorting(),
                                Sort.Order.asc("name").ignoreCase()));
        SearchProtectionException nullHandling = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .sorting(sorting -> sorting.allowNullHandling(false))
                        .build())
                        .recordSort(
                                comparisonDefinition.field("name").orElseThrow().sorting(),
                                Sort.Order.asc("name").nullsLast()));
        SearchProtectionException relationOrders = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder()
                        .sorting(sorting -> sorting.maxRelationOrders(0))
                        .build())
                        .recordSort(
                                sortDefinition.field("supplierName").orElseThrow().sorting(),
                                Sort.Order.asc("supplierName")));
        SearchProtectionException queryWithToMany = thrownBy(
                SearchProtectionException.class,
                () -> {
                    SearchPolicy policy = SearchPolicy.builder()
                            .query(query -> query.allowWithToManyFilter(false))
                            .build();
                    SearchProtectionContext protection = context(policy);
                    protection.recordQuery("tablet");
                    protection.recordComparison(
                            comparisonDefinition(policy).field("reviewRating").orElseThrow(),
                            descriptor(EQUAL),
                            1,
                            List.of("5"));
                    protection.completeRequest();
                });
        SearchProtectionException queryWithRelationSort = thrownBy(
                SearchProtectionException.class,
                () -> {
                    SearchProtectionContext protection = context(SearchPolicy.builder()
                            .query(query -> query.allowWithRelationSort(false))
                            .build());
                    protection.recordQuery("tablet");
                    protection.recordSort(
                            sortDefinition.field("supplierName").orElseThrow().sorting(),
                            Sort.Order.asc("supplierName"));
                    protection.completeRequest();
                });

        assertRule(ignoreCase, "sorting.allow-ignore-case", 1, 0);
        assertRule(nullHandling, "sorting.allow-null-handling", 1, 0);
        assertRule(relationOrders, "sorting.max-relation-orders", 1, 0);
        assertRule(queryWithToMany, "query.allow-with-to-many-filter", 1, 0);
        assertRule(queryWithRelationSort, "query.allow-with-relation-sort", 1, 0);
    }

    @Test
    void rejectsQueryLengthLimitsAndIgnoresAbsentQuery() {
        SearchProtectionContext protection = context(SearchPolicy.defaults());

        assertDoesNotThrow(() -> protection.recordQuery(null));

        SearchProtectionException tooLong = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder().query(query -> query.maxLength(3)).build()).recordQuery("abcd"));
        SearchProtectionException tooShort = thrownBy(
                SearchProtectionException.class,
                () -> context(SearchPolicy.builder().query(query -> query.minLength(3)).build()).recordQuery("ab"));

        assertRule(tooLong, "query.max-length", 4, 3);
        assertRule(tooShort, "query.min-length", 2, 3);
    }

    @Test
    void pageModeCanRejectToManyCountWhileSliceModeAcceptsSameShape() {
        SearchPolicy policy = SearchPolicy.builder()
                .paging(paging -> paging.page(page -> page.allowToManyCount(false)))
                .build();
        SearchCompiler compiler = compiler(policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .paging()
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchProtectionException pageException = thrownBy(
                SearchProtectionException.class,
                () -> compiler.compile(
                        "reviewRating==5",
                        null,
                        pageRequest,
                        definition));

        assertRule(pageException, "paging.page.allow-to-many-count", 1, 0);
        assertDoesNotThrow(() -> compiler.compileSlice(
                "reviewRating==5",
                null,
                pageRequest,
                definition));
    }

    @Test
    void rejectsFreeTextQueryWhenValidatorIsRequiredButMissing() {
        SearchPolicy policy = SearchPolicy.builder()
                .query(query -> query.requireValidator(true))
                .build();
        QuerySearchGuard guard = new QuerySearchGuard(policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .query(query -> query.specification(term -> Specification.unrestricted()))
                .build();

        SearchQueryValidationException exception = thrownBy(
                SearchQueryValidationException.class,
                () -> guard.specification("tablet", definition));

        assertEquals(SearchQueryValidationException.QUERY_RULES_FORBIDDEN, exception.code());
    }

    @Test
    void canDisableSortingThroughRelationsWithoutPerFieldConfiguration() {
        SearchPolicy policy = SearchPolicy.builder()
                .sorting(sorting -> sorting.allowRelationSorting(false))
                .build();
        PageableSearchGuard guard = new PageableSearchGuard(policy);
        SearchDefinition<Product> definition = SearchDefinition.builder(policy)
                .entity(Product.class)
                .fields(fields -> fields.add("supplierName", String.class)
                        .path("supplier.name")
                        .sortable())
                .paging()
                .build();

        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by("supplierName"));
        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> guard.pageable(pageRequest, definition));

        assertRule(exception, "sorting.allow-relation-sorting", 1, 0);
    }

    @Test
    void boundedUnpagedCanBeRejectedWhenCombinedWithFreeTextQuery() {
        SearchPolicy policy = SearchPolicy.builder()
                .paging(paging -> paging.allowUnpaged(true))
                .query(query -> query.allowWithUnpaged(false))
                .build();
        SearchCompiler compiler = compiler(policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .query(query -> query.specification(term -> Specification.unrestricted()))
                .build();

        Pageable pageable = Pageable.unpaged();
        SearchProtectionException exception = thrownBy(
                SearchProtectionException.class,
                () -> compiler.compile(null, "tablet", pageable, definition));

        assertRule(exception, "query.allow-with-unpaged", 1, 0);
    }

    @Test
    void hardenedSyntacticAndProtectionDefaultsRemainExplicit() {
        SearchPolicy policy = SearchPolicy.defaults();

        assertEquals(48, policy.rsql().maxNodes());
        assertEquals(24, policy.filter().maxComparisons());
        assertEquals(75, policy.filter().maxArgumentsTotal());
        assertEquals(50, policy.filter().maxInValues());
        assertEquals(25, policy.filter().maxNotInValues());
        assertEquals(16, policy.filter().maxOrBranches());
        assertEquals(5_000, policy.paging().maxOffset());
        assertEquals(40, policy.paging().defaultUnpagedSize());
        assertEquals(100, policy.paging().maxUnpagedSize());
        assertEquals(1, policy.paging().page().maxToManyPaths());
    }

    private static SearchCompiler compiler(SearchPolicy policy) {
        return new SearchCompiler(
                TestRsqlEngines.builder()
                        .conversionService(
                                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
    }

    private static SearchProtectionContext context(SearchPolicy policy) {
        return new SearchProtectionContext(policy, SearchProtectionContext.Mode.PAGE);
    }

    private static RsqlOperatorDescriptor descriptor(RsqlOperator operator) {
        return DefaultRsqlOperatorDescriptors.all().stream()
                .filter(candidate -> operator.equals(candidate.operator()))
                .findFirst()
                .orElseThrow();
    }

    private static SearchSorting sortingWithToManyTopology() throws ReflectiveOperationException {
        Constructor<SearchSorting> constructor = SearchSorting.class.getDeclaredConstructor(
                boolean.class,
                String.class,
                Set.class,
                boolean.class,
                Set.class,
                SearchPath.Topology.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                true,
                "tags",
                Set.of(Sort.Direction.ASC),
                false,
                Set.of(Sort.NullHandling.NATIVE),
                new SearchPath.Topology(1, Set.of("tags"), Set.of("tags")));
    }

    private static SearchDefinition<TestTypes.Product> comparisonDefinition(SearchPolicy policy) {
        return SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("amount", java.math.BigDecimal.class)
                            .path("price")
                            .filterable(filter -> filter.allow(EQUAL))
                            .sortable();
                    fields.add("name", String.class)
                            .filterable(filter -> filter.allow(LIKE))
                            .sortable();
                    fields.add("reviewRating", Integer.class)
                            .path("reviews.rating")
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();
    }

    private static void assertRule(
            SearchProtectionException exception,
            String rule,
            long actual,
            long limit) {
        assertEquals(SearchProtectionException.PROTECTION_RULE_EXCEEDED, exception.code());
        assertEquals(rule, exception.rule());
        assertEquals(actual, exception.actual());
        assertEquals(limit, exception.limit());
    }
}
