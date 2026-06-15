package io.github.ggomarighetti.searchhelper.compile;

import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.exception.SearchProtectionException;
import io.github.ggomarighetti.searchhelper.exception.SearchQueryValidationException;
import io.github.ggomarighetti.searchhelper.integration.bench.domain.Product;
import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import io.github.ggomarighetti.searchhelper.rsql.operator.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperatorDescriptor;
import io.github.ggomarighetti.searchhelper.unit.TestTypes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.LIKE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchProtectionTest {
    @Test
    void rejectsShortToManyFilterByExplicitTopologyRule() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxToManyPaths(0))
                .build();
        RsqlSearchGuard guard = new RsqlSearchGuard(
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance(),
                policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        SearchProtectionException exception = assertThrows(
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
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance(),
                policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("taxId", String.class)
                        .path("owner.taxIdentifier")
                        .filterable(filter -> filter.allow(IN)))
                .build();

        SearchProtectionException exception = assertThrows(
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
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance(),
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

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification(
                        "categoryCode==LAPTOP,supplierName==Acme",
                        definition));

        assertRule(exception, "filter.max-or-join-roots", 2, 1);
    }

    @Test
    void likeProtectionDoesNotTreatEscapedTrailingWildcardAsPatternWildcard() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.like(like -> like.allowTrailingWildcard(false)))
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

        SearchProtectionContext protection = new SearchProtectionContext(policy, SearchCompilationMode.PAGE);
        assertDoesNotThrow(() -> protection.recordComparison(
                definition.field("name").orElseThrow(),
                descriptor,
                1,
                List.of("abc\\*")));

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> new SearchProtectionContext(policy, SearchCompilationMode.PAGE).recordComparison(
                        definition.field("name").orElseThrow(),
                        descriptor,
                        1,
                        List.of("abc*")));

        assertRule(exception, "filter.like.allow-trailing-wildcard", 1, 0);
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

        SearchProtectionException pageException = assertThrows(
                SearchProtectionException.class,
                () -> compiler.compile(
                        "reviewRating==5",
                        null,
                        PageRequest.of(0, 10),
                        definition));

        assertRule(pageException, "paging.page.allow-to-many-count", 1, 0);
        assertDoesNotThrow(() -> compiler.compileSlice(
                "reviewRating==5",
                null,
                PageRequest.of(0, 10),
                definition));
    }

    @Test
    void rejectsFreeTextQueryWhenValidatorIsRequiredButMissing() {
        SearchPolicy policy = SearchPolicy.builder()
                .query(query -> query.requireValidator(true))
                .build();
        SearchQueryGuard guard = new SearchQueryGuard(policy);
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy)
                .entity(TestTypes.Product.class)
                .query(query -> query.specification(term -> Specification.unrestricted()))
                .build();

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tablet", definition));

        assertEquals(SearchQueryValidationException.QUERY_RULES_FORBIDDEN, exception.code());
    }

    @Test
    void canDisableSortingThroughRelationsWithoutPerFieldConfiguration() {
        SearchPolicy policy = SearchPolicy.builder()
                .sorting(sorting -> sorting.allowRelationSorting(false))
                .build();
        SearchPageableGuard guard = new SearchPageableGuard(policy);
        SearchDefinition<Product> definition = SearchDefinition.builder(policy)
                .entity(Product.class)
                .fields(fields -> fields.add("supplierName", String.class)
                        .path("supplier.name")
                        .sortable())
                .paging()
                .build();

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.pageable(
                        PageRequest.of(0, 25, Sort.by("supplierName")),
                        definition));

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

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> compiler.compile(null, "tablet", Pageable.unpaged(), definition));

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
                io.github.ggomarighetti.searchhelper.rsql.SearchRsqlEngine.builder()
                        .conversionService(
                                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
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
