package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchPageableValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.validator.cfg.defs.MaxDef;
import org.hibernate.validator.cfg.defs.MinDef;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

class SearchPageableGuardTest {
    private final SearchPageableGuard guard = new SearchPageableGuard();

    @Test
    void translatesSortableSelectorsToInternalPaths() {
        Pageable pageable = guard.pageable(
                PageRequest.of(2, 25, Sort.by(Sort.Order.asc("customerName"), Sort.Order.desc("amount"))),
                definition());

        List<Sort.Order> orders = pageable.getSort().toList();

        assertEquals(2, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
        assertEquals("customer.sortName", orders.get(0).getProperty());
        assertEquals(ASC, orders.get(0).getDirection());
        assertEquals("price", orders.get(1).getProperty());
        assertEquals(DESC, orders.get(1).getDirection());
    }

    @Test
    void preservesSortOrderOptionsWhenTranslatingProperty() {
        Sort.Order source = Sort.Order.asc("customerName").ignoreCase().nullsLast();
        SearchPolicy policy = SearchPolicy.builder()
                .sorting(sorting -> sorting
                        .allowIgnoreCase(true)
                        .allowNullHandling(true))
                .build();

        Pageable pageable = guard.pageable(PageRequest.of(0, 25, Sort.by(source)), definition(policy));
        Sort.Order translated = pageable.getSort().getOrderFor("customer.sortName");

        assertEquals(ASC, translated.getDirection());
        assertTrue(translated.isIgnoreCase());
        assertEquals(Sort.NullHandling.NULLS_LAST, translated.getNullHandling());
    }

    @Test
    void boundsUnpagedPageableAndTranslatesSortWithoutRequiringPagingRules() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging.allowUnpaged(true)))
                .fields(fields -> fields.add("customerName", String.class)
                        .path("customer.name")
                        .sortable(sort -> sort.path("customer.sortName")))
                .build();

        Pageable pageable = guard.pageable(Pageable.unpaged(Sort.by("customerName")), definition);

        assertTrue(pageable.isPaged());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(40, pageable.getPageSize());
        assertEquals("customer.sortName", pageable.getSort().toList().get(0).getProperty());
    }

    @Test
    void rejectsDifferentSortSelectorsThatResolveToSameInternalPath() {
        SearchDefinition<TestTypes.Product> definition = duplicateSortPathDefinition();
        PageRequest pageRequest = PageRequest.of(0, 25,
                Sort.by(Sort.Order.asc("legacyAmount"), Sort.Order.desc("amount")));

        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageRequest, definition));

        assertValidationCode(exception, SearchPageableValidationException.SORT_LIMIT_EXCEEDED);
    }

    @Test
    void allowsSortAliasesThatSharePathWhenUsedIndividually() {
        SearchDefinition<TestTypes.Product> definition = duplicateSortPathDefinition();

        Pageable legacyAlias = guard.pageable(
                PageRequest.of(0, 25, Sort.by(Sort.Order.asc("legacyAmount"))),
                definition);
        Pageable currentAlias = guard.pageable(
                PageRequest.of(0, 25, Sort.by(Sort.Order.desc("amount"))),
                definition);

        assertEquals("price", legacyAlias.getSort().toList().get(0).getProperty());
        assertEquals(ASC, legacyAlias.getSort().toList().get(0).getDirection());
        assertEquals("price", currentAlias.getSort().toList().get(0).getProperty());
        assertEquals(DESC, currentAlias.getSort().toList().get(0).getDirection());
    }

    @Test
    void rejectsUnpagedPageableByDefault() {
        Pageable pageable = Pageable.unpaged();
        SearchDefinition<TestTypes.Product> definition = definition();
        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageable, definition));

        assertValidationCode(exception, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
    }

    @Test
    void boundsAllowedUnpagedPageableWithDefinitionLimit() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging
                        .allowUnpaged(true)
                        .unpagedSize(12)))
                .fields(fields -> fields.add("email", String.class))
                .build();

        Pageable pageable = guard.pageable(Pageable.unpaged(), definition);

        assertTrue(pageable.isPaged());
        assertEquals(0, pageable.getPageNumber());
        assertEquals(12, pageable.getPageSize());
        assertTrue(pageable.getSort().isUnsorted());
    }

    @Test
    void rejectsUnpagedLimitAboveConfiguredMaximum() {
        var builder = SearchPolicy.builder()
                .paging(paging -> paging
                        .maxUnpagedSize(10)
                        .unpagedSize(11));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals(
                "paging.defaultUnpagedSize must be less than or equal to paging.maxUnpagedSize",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownSortSelector() {
        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by("passwordHash"));
        SearchDefinition<TestTypes.Product> definition = definition();
        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageRequest, definition));

        assertValidationCode(exception, SearchPageableValidationException.SORT_RULES_FORBIDDEN);
    }

    @Test
    void rejectsFieldThatDoesNotDeclareSorting() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .paging()
                .build();
        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by("email"));

        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageRequest, definition));

        assertValidationCode(exception, SearchPageableValidationException.SORT_RULES_FORBIDDEN);
    }

    @Test
    void rejectsSortDirectionThatIsNotAllowed() {
        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by(Sort.Order.asc("createdAt")));
        SearchDefinition<TestTypes.Product> definition = definition();
        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageRequest, definition));

        assertValidationCode(exception, SearchPageableValidationException.SORT_RULES_FORBIDDEN);
    }

    @Test
    void rejectsPagedPageableWhenPagingIsNotDeclared() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class).sortable())
                .build();
        PageRequest pageRequest = PageRequest.of(0, 25);

        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(pageRequest, definition));

        assertValidationCode(exception, SearchPageableValidationException.PAGE_RULES_FORBIDDEN);
    }

    @Test
    void validatesPageAndSizeWithHibernateRules() {
        SearchDefinition<TestTypes.Product> definition = definition(SearchPolicy.builder()
                .paging(paging -> paging.maxSize(200))
                .build());

        assertEquals(3, guard.pageable(PageRequest.of(3, 50), definition).getPageNumber());

        PageRequest invalidPage = PageRequest.of(4, 50);
        SearchPageableValidationException pageException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(invalidPage, definition));
        PageRequest invalidSize = PageRequest.of(0, 101);
        SearchPageableValidationException sizeException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(invalidSize, definition));

        assertValidationCode(pageException, SearchPageableValidationException.PAGE_RULES_FORBIDDEN);
        assertValidationCode(sizeException, SearchPageableValidationException.PAGE_RULES_FORBIDDEN);
        assertEquals("page", pageException.violations().get(0).path());
        assertTrue(pageException.violations().get(0).constraint().endsWith(".Max"));
        assertEquals("size", sizeException.violations().get(0).path());
        assertTrue(sizeException.violations().get(0).constraint().endsWith(".Max"));
        var violations = pageException.violations();
        assertThrows(UnsupportedOperationException.class, violations::clear);
    }

    @Test
    void rejectsPageableSafetyLimitsBeforeHibernateRules() {
        SearchDefinition<TestTypes.Product> definition = definition();
        SearchPolicy minPolicy = SearchPolicy.builder()
                .paging(paging -> paging.minPage(1).minSize(2))
                .build();
        SearchDefinition<TestTypes.Product> minDefinition = definition(minPolicy);
        PageRequest tooSmallPage = PageRequest.of(0, 2);
        SearchPageableValidationException minPageException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(tooSmallPage, minDefinition));
        PageRequest tooSmallSize = PageRequest.of(1, 1);
        SearchPageableValidationException minSizeException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(tooSmallSize, minDefinition));
        PageRequest invalidPage = PageRequest.of(101, 1);
        SearchPageableValidationException pageException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(invalidPage, definition));
        PageRequest invalidSize = PageRequest.of(0, 101);
        SearchPageableValidationException sizeException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(invalidSize, definition));
        SearchPolicy offsetPolicy = SearchPolicy.builder()
                .paging(paging -> paging.maxPage(200).maxSize(200))
                .build();
        SearchDefinition<TestTypes.Product> offsetDefinition = definition(offsetPolicy);
        PageRequest offsetPage = PageRequest.of(100, 101);
        SearchPageableValidationException offsetException = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(offsetPage, offsetDefinition));

        assertValidationCode(minPageException, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
        assertValidationCode(minSizeException, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
        assertValidationCode(pageException, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
        assertValidationCode(sizeException, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
        assertValidationCode(offsetException, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
    }

    @Test
    void partialDefinitionLimitsPreserveRuntimeGuardPolicy() {
        SearchPageableGuard limitedGuard = new SearchPageableGuard(SearchPolicy.builder()
                .paging(paging -> paging.maxSize(5))
                .build());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging.allowUnpaged(true)))
                .fields(fields -> fields.add("email", String.class))
                .paging()
                .build();

        Pageable boundedUnpaged = limitedGuard.pageable(Pageable.unpaged(), definition);
        assertTrue(boundedUnpaged.isPaged());
        assertEquals(40, boundedUnpaged.getPageSize());

        PageRequest oversizedPage = PageRequest.of(0, 6);
        SearchPageableValidationException exception = assertThrows(
                SearchPageableValidationException.class,
                () -> limitedGuard.pageable(oversizedPage, definition));
        assertValidationCode(exception, SearchPageableValidationException.PAGE_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsSortSafetyLimits() {
        SearchDefinition<TestTypes.Product> definition = definition();
        PageRequest tooManySorts = PageRequest.of(0, 25, Sort.by("customerName", "amount", "createdAt", "email"));
        SearchPageableValidationException tooManyOrders = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(tooManySorts, definition));
        PageRequest duplicatedSort = PageRequest.of(0, 25,
                Sort.by(Sort.Order.asc("customerName"), Sort.Order.desc("customerName")));
        SearchPageableValidationException duplicatedSelector = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(duplicatedSort, definition));
        PageRequest ignoreCaseSort = PageRequest.of(0, 25, Sort.by(Sort.Order.asc("amount").ignoreCase()));
        SearchPageableValidationException ignoreCase = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(ignoreCaseSort, definition));
        PageRequest nullHandlingSort = PageRequest.of(0, 25, Sort.by(Sort.Order.asc("amount").nullsLast()));
        SearchPageableValidationException nullHandling = assertThrows(
                SearchPageableValidationException.class,
                () -> guard.pageable(nullHandlingSort, definition));

        assertValidationCode(tooManyOrders, SearchPageableValidationException.SORT_LIMIT_EXCEEDED);
        assertValidationCode(duplicatedSelector, SearchPageableValidationException.SORT_LIMIT_EXCEEDED);
        assertValidationCode(ignoreCase, SearchPageableValidationException.SORT_LIMIT_EXCEEDED);
        assertValidationCode(nullHandling, SearchPageableValidationException.SORT_LIMIT_EXCEEDED);
    }

    private static SearchDefinition<TestTypes.Product> definition() {
        return definition(null);
    }

    private static SearchDefinition<TestTypes.Product> definition(SearchPolicy limits) {
        SearchDefinition.Builder<TestTypes.Product> builder =
                SearchDefinition.builder().entity(TestTypes.Product.class);
        if (limits != null) {
            builder.limits(limits);
        }
        return builder
                .fields(fields -> {
                    fields.add("customerName", String.class)
                            .path("customer.name")
                            .sortable(sort -> sort
                                    .path("customer.sortName")
                                    .allowIgnoreCase()
                                    .allowNullHandling(Sort.NullHandling.NULLS_LAST));
                    fields.add("amount", BigDecimal.class)
                            .path("price")
                            .sortable();
                    fields.add("createdAt", Instant.class)
                            .sortable(sort -> sort.allow(DESC));
                    fields.add("email", String.class);
                })
                .paging(paging -> {
                    paging.page(page -> {
                        page.rule(new MinDef().value(0));
                        page.rule(new MaxDef().value(3));
                    });
                    paging.size(size -> {
                        size.rule(new MinDef().value(1));
                        size.rule(new MaxDef().value(100));
                    });
                })
                .build();
    }

    private static SearchDefinition<TestTypes.Product> duplicateSortPathDefinition() {
        return SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("legacyAmount", BigDecimal.class)
                            .sortable(sort -> sort.path("price"));
                    fields.add("amount", BigDecimal.class)
                            .sortable(sort -> sort.path("price"));
                })
                .paging()
                .build();
    }

    private static void assertValidationCode(
            SearchPageableValidationException exception, String expectedCode) {
        assertEquals(expectedCode, exception.code());
    }
}
