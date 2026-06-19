package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.unit.TestRsqlEngines;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.compile.CompiledSearch;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.data.jpa.domain.Specification.unrestricted;

class SearchCompilerTest {
    private final SearchCompiler compiler = new SearchCompiler(TestRsqlEngines.defaults(), SearchPolicy.defaults());

    @Test
    void constructsWithExplicitPolicy() {
        SearchCompiler customCompiler = new SearchCompiler(TestRsqlEngines.defaults(), SearchPolicy.defaults());

        assertNotNull(customCompiler);
    }

    @Test
    void combinesRsqlFilterAndQuerySpecification() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        SearchDefinition<TestTypes.Product> definition = definition(capturedQuery);
        Specification<TestTypes.Product> applicationSpecification = unrestricted();

        Specification<TestTypes.Product> specification = compiler.compile(
                "taxId==20123456789",
                "tablet",
                PageRequest.of(0, 10),
                definition,
                applicationSpecification).specification();

        assertNotNull(specification);
        assertEquals("tablet", capturedQuery.get());
    }

    @Test
    void exposesOnlyCompileAsPublicRequestEntryPoint() {
        Set<String> publicMethods = Arrays.stream(SearchCompiler.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(SearchCompiler.class))
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(publicMethods.contains("compile"));
        assertTrue(publicMethods.contains("compileSlice"));
        assertFalse(publicMethods.contains("specification"));
        assertFalse(publicMethods.contains("pageable"));
    }

    @Test
    void combinesMultipleApplicationSpecificationsWithAnd() {
        AtomicInteger andCalls = new AtomicInteger();
        AtomicInteger orCalls = new AtomicInteger();
        Predicate first = predicate();
        Predicate second = predicate();
        Predicate combined = predicate();

        Specification<TestTypes.Product> specification = compiler.compile(
                null,
                null,
                PageRequest.of(0, 10),
                emptyDefinition(),
                (root, query, builder) -> first,
                (root, query, builder) -> second).specification();

        Predicate result = specification.toPredicate(
                null,
                null,
                criteriaBuilder(andCalls, orCalls, combined));

        assertSame(combined, result);
        assertEquals(1, andCalls.get());
        assertEquals(0, orCalls.get());
    }

    @Test
    void appliesApplicationSpecificationsWhenFilterAndQueryAreEmpty() {
        AtomicInteger invocations = new AtomicInteger();
        Specification<TestTypes.Product> applicationSpecification = (root, query, builder) -> {
            invocations.incrementAndGet();
            return null;
        };

        Specification<TestTypes.Product> specification = compiler.compile(
                " ",
                null,
                PageRequest.of(0, 10),
                emptyDefinition(),
                applicationSpecification).specification();

        specification.toPredicate(null, null, null);

        assertEquals(1, invocations.get());
    }

    @Test
    void preservesCurrentBehaviorWithoutApplicationSpecifications() {
        Specification<TestTypes.Product> specification = compiler.compile(
                null,
                "tablet",
                PageRequest.of(0, 10),
                definition(new AtomicReference<>())).specification();

        assertNotNull(specification);
    }

    @Test
    void rejectsNullApplicationSpecifications() {
        @SuppressWarnings("unchecked")
        Specification<TestTypes.Product>[] nullSpecifications = null;
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchDefinition<TestTypes.Product> definition = emptyDefinition();

        NullPointerException arrayException = assertThrows(
                NullPointerException.class,
                () -> compiler.compile(null, null, pageRequest, definition, nullSpecifications));
        NullPointerException elementException = assertThrows(
                NullPointerException.class,
                () -> compiler.compile(
                        null,
                        null,
                        pageRequest,
                        definition,
                        (Specification<TestTypes.Product>) null));

        assertEquals("specifications must not be null", arrayException.getMessage());
        assertEquals("specifications must not contain null values", elementException.getMessage());
    }

    @Test
    void returnsCompiledSpecificationAndPageable() {
        CompiledSearch<TestTypes.Product> compiled = compiler.compile(
                "taxId==20123456789",
                "tablet",
                PageRequest.of(0, 25, Sort.by("customerName")),
                definition(new AtomicReference<>()));

        assertNotNull(compiled.specification());
        assertEquals(0, compiled.pageable().getPageNumber());
        assertEquals(25, compiled.pageable().getPageSize());
        assertEquals("customer.name", compiled.pageable().getSort().toList().get(0).getProperty());
    }

    @Test
    void supportsEmptyFilterQueryOrBoth() {
        SearchDefinition<TestTypes.Product> queryDefinition = definition(new AtomicReference<>());
        SearchDefinition<TestTypes.Product> emptyDefinition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.paging(paging -> paging.allowUnpaged(true)))
                .fields(fields -> fields.add("taxId", String.class)
                        .path("owner.taxIdentifier")
                        .filterable(filter -> filter.allow(EQUAL)))
                .paging()
                .build();

        assertNotNull(compiler.compile("", "tablet", PageRequest.of(0, 10), queryDefinition).specification());
        assertNotNull(compiler.compile(
                "taxId==20123456789",
                "",
                PageRequest.of(0, 10),
                emptyDefinition).specification());

        CompiledSearch<TestTypes.Product> compiled =
                compiler.compile(null, "   ", Pageable.unpaged(), emptyDefinition);

        assertNotNull(compiled.specification());
        assertTrue(compiled.pageable().isPaged());
        assertEquals(40, compiled.pageable().getPageSize());
    }

    @Test
    void inheritsProtectionPolicyFromCustomRsqlGuard() {
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxInValues(1))
                .build();
        SearchCompiler customCompiler = new SearchCompiler(
                TestRsqlEngines.builder()
                        .conversionService(ApplicationConversionService.getSharedInstance())
                        .build(),
                policy);
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchDefinition<TestTypes.Product> definition = definition(new AtomicReference<>());

        assertThrows(
                SearchProtectionException.class,
                () -> customCompiler.compile(
                        "taxId=in=(1,2)",
                        null,
                        pageRequest,
                        definition));
    }

    @Test
    void validatesDefinitionWithoutRsqlAndCachesSuccessfulValidationByInstance() {
        AtomicInteger validations = new AtomicInteger();
        SearchCompiler customCompiler = new SearchCompiler(
                TestRsqlEngines.defaults(),
                SearchPolicy.defaults(),
                List.of(definition -> validations.incrementAndGet()));
        SearchDefinition<TestTypes.Product> definition = emptyDefinition();

        customCompiler.compile(null, null, PageRequest.of(0, 10), definition);
        customCompiler.compile(" ", " ", PageRequest.of(1, 10), definition);
        customCompiler.compile(null, null, PageRequest.of(0, 10), emptyDefinition());

        assertEquals(2, validations.get());
    }

    @Test
    void doesNotCacheFailedDefinitionValidation() {
        AtomicInteger validations = new AtomicInteger();
        SearchCompiler customCompiler = new SearchCompiler(
                TestRsqlEngines.defaults(),
                SearchPolicy.defaults(),
                List.of(definition -> {
                    validations.incrementAndGet();
                    throw new IllegalStateException("invalid definition");
                }));
        SearchDefinition<TestTypes.Product> definition = emptyDefinition();
        PageRequest pageRequest = PageRequest.of(0, 10);

        assertThrows(
                IllegalStateException.class,
                () -> customCompiler.compile(null, null, pageRequest, definition));
        assertThrows(
                IllegalStateException.class,
                () -> customCompiler.compile(null, null, pageRequest, definition));

        assertEquals(2, validations.get());
    }

    @Test
    void validatesDefinitionOnlyOnceUnderConcurrentCompilation() throws Exception {
        AtomicInteger validations = new AtomicInteger();
        SearchCompiler customCompiler = new SearchCompiler(
                TestRsqlEngines.defaults(),
                SearchPolicy.defaults(),
                List.of(definition -> validations.incrementAndGet()));
        SearchDefinition<TestTypes.Product> definition = emptyDefinition();
        int concurrentCompilations = 16;
        CyclicBarrier barrier = new CyclicBarrier(concurrentCompilations);
        List<Callable<CompiledSearch<TestTypes.Product>>> tasks =
                java.util.stream.IntStream.range(0, concurrentCompilations)
                        .mapToObj(index -> (Callable<CompiledSearch<TestTypes.Product>>) () -> {
                            barrier.await();
                            return customCompiler.compile(
                                    null,
                                    null,
                                    PageRequest.of(index, 10),
                                    definition);
                        })
                        .toList();

        var executor = Executors.newFixedThreadPool(concurrentCompilations);
        try {
            for (var result : executor.invokeAll(tasks)) {
                assertNotNull(result.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, validations.get());
    }

    private static SearchDefinition<TestTypes.Product> definition(AtomicReference<String> capturedQuery) {
        return SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("taxId", String.class)
                            .path("owner.taxIdentifier")
                            .filterable(filter -> filter.allow(EQUAL, IN));
                    fields.add("customerName", String.class)
                            .path("customer.name")
                            .sortable();
                })
                .query(query -> query.specification(term -> {
                    capturedQuery.set(term);
                    return unrestricted();
                }))
                .paging()
                .build();
    }

    private static SearchDefinition<TestTypes.Product> emptyDefinition() {
        return SearchDefinition.builder().entity(TestTypes.Product.class)
                .paging()
                .build();
    }

    private static Predicate predicate() {
        return (Predicate) Proxy.newProxyInstance(
                Predicate.class.getClassLoader(),
                new Class<?>[] {Predicate.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CriteriaBuilder criteriaBuilder(
            AtomicInteger andCalls,
            AtomicInteger orCalls,
            Predicate combined) {
        return (CriteriaBuilder) Proxy.newProxyInstance(
                CriteriaBuilder.class.getClassLoader(),
                new Class<?>[] {CriteriaBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("and")) {
                        andCalls.incrementAndGet();
                        return combined;
                    }
                    if (method.getName().equals("or")) {
                        orCalls.incrementAndGet();
                        return combined;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
