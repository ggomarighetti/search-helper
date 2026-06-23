package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.filter.FilterValidationResult;
import io.github.ggomarighetti.jparsqlsearch.page.SearchPaging;
import io.github.ggomarighetti.jparsqlsearch.query.SearchQuery;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaPredicateContext;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorArity;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorBinding;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.cfg.defs.MinDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

class SmallApiCoverageTest {
    @Test
    void disabledPagingRejectsValuesAndReturnsNoViolations() {
        SearchPaging paging = SearchPaging.disabled();

        assertFalse(paging.accepts(0, 10));
        assertFalse(paging.acceptsPage(0));
        assertFalse(paging.acceptsSize(10));
        assertEquals(List.of(), paging.pageViolations(-1));
        assertEquals(List.of(), paging.sizeViolations(100));
    }

    @Test
    void pagingAppliesPageAndSizeRulesIndependently() {
        SearchPaging paging = SearchPaging.builder()
                .page(page -> page.rule(new MinDef().value(1)))
                .size(size -> size.rule(new MinDef().value(2)))
                .build();

        assertTrue(paging.accepts(1, 2));
        assertTrue(paging.acceptsPage(1));
        assertTrue(paging.acceptsSize(2));
        assertFalse(paging.accepts(0, 2));
        assertFalse(paging.accepts(1, 1));
        assertFalse(paging.acceptsPage(0));
        assertFalse(paging.acceptsSize(1));
        assertEquals(1, paging.pageViolations(0).size());
        assertEquals(1, paging.sizeViolations(1).size());
        thrownBy(NullPointerException.class, () -> SearchPaging.builder().page(null));
        thrownBy(NullPointerException.class, () -> SearchPaging.builder().size(null));
        thrownBy(NullPointerException.class, () -> new SearchPaging.Rules<Integer>().rule(null));
    }

    @Test
    void disabledQueryRejectsTextAndCannotBuildSpecification() {
        SearchQuery<TestTypes.Product> query = SearchQuery.disabled();

        assertFalse(query.accepts("needle"));
        assertFalse(query.hasRules());
        assertEquals(List.of(), query.violations("needle"));
        thrownBy(IllegalStateException.class, () -> query.toSpecification("needle"));
    }

    @Test
    void queryBuilderValidatesRequiredAndDuplicateSpecification() {
        SearchQuery.Builder<TestTypes.Product> builder = SearchQuery.builder();
        builder.specification(term -> (root, criteria, criteriaBuilder) -> criteriaBuilder.conjunction());

        assertNotNull(thrownBy(IllegalArgumentException.class, () -> SearchQuery.builder().build()));
        thrownBy(NullPointerException.class, () -> SearchQuery.builder().rule(null));
        thrownBy(NullPointerException.class, () -> SearchQuery.builder().specification(null));
        thrownBy(IllegalArgumentException.class, () -> builder.specification(term ->
                (root, criteria, criteriaBuilder) -> criteriaBuilder.conjunction()));
    }

    @Test
    void queryWithRulesReportsViolationsAndDelegatesSpecificationFactory() {
        var specification = (org.springframework.data.jpa.domain.Specification<TestTypes.Product>)
                (root, criteria, criteriaBuilder) -> criteriaBuilder.conjunction();
        SearchQuery<TestTypes.Product> query = SearchQuery.<TestTypes.Product>builder()
                .rule(new SizeDef().min(3))
                .specification(term -> specification)
                .build();

        assertTrue(query.enabled());
        assertTrue(query.hasRules());
        assertTrue(query.accepts("abcd"));
        assertFalse(query.accepts("ab"));
        assertEquals(1, query.violations("ab").size());
        assertSame(specification, query.toSpecification("abcd"));
    }

    @Test
    void disabledAndCustomizedSortingExposeAcceptanceRules() {
        SearchSorting disabled = SearchSorting.disabled();
        SearchSorting sorting = SearchSorting.builder()
                .allow(ASC)
                .allowIgnoreCase()
                .allowNullHandling(Sort.NullHandling.NULLS_FIRST)
                .build(TestTypes.Product.class, "email", String.class, "email", io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy.defaults().paths());

        assertFalse(disabled.accepts(ASC));
        assertFalse(disabled.acceptsIgnoreCase(false));
        assertFalse(disabled.acceptsNullHandling(Sort.NullHandling.NATIVE));
        thrownBy(NullPointerException.class, () -> disabled.accepts(null));
        thrownBy(NullPointerException.class, () -> disabled.acceptsNullHandling(null));
        assertTrue(sorting.accepts(ASC));
        assertFalse(sorting.accepts(DESC));
        assertTrue(sorting.ignoreCase());
        assertTrue(sorting.nullHandling().contains(Sort.NullHandling.NULLS_FIRST));
        assertTrue(sorting.acceptsIgnoreCase(true));
        assertTrue(sorting.acceptsIgnoreCase(false));
        assertTrue(sorting.acceptsNullHandling(Sort.NullHandling.NULLS_FIRST));
        assertFalse(sorting.acceptsNullHandling(Sort.NullHandling.NULLS_LAST));
        thrownBy(IllegalArgumentException.class, () -> SearchSorting.builder().allow());
        thrownBy(NullPointerException.class, () -> SearchSorting.builder().allow((org.springframework.data.domain.Sort.Direction) null));
        thrownBy(IllegalArgumentException.class, () -> SearchSorting.builder().allowNullHandling());
        thrownBy(NullPointerException.class, () -> SearchSorting.builder().allowNullHandling((Sort.NullHandling) null));
    }

    @Test
    void filterValidationErrorsExposeOptionalDetailsAndRejectInvalidShapes() {
        RuleViolation violation = new RuleViolation("value", "bad", "{bad}", "Constraint");
        FilterValidationResult.Error conversion = FilterValidationResult.Error.conversionFailed(0, Integer.class);
        FilterValidationResult.Error argument = FilterValidationResult.Error.argumentRule(1, violation);
        FilterValidationResult.Error arguments = FilterValidationResult.Error.argumentsRule(violation);

        assertEquals(0, conversion.optionalArgumentIndex().orElseThrow());
        assertEquals(Integer.class.getName(), conversion.optionalTargetType().orElseThrow());
        assertTrue(conversion.optionalViolation().isEmpty());
        assertEquals(1, argument.optionalArgumentIndex().orElseThrow());
        assertEquals(violation, argument.optionalViolation().orElseThrow());
        assertTrue(arguments.optionalArgumentIndex().isEmpty());
        thrownBy(IllegalArgumentException.class, () ->
                new FilterValidationResult.Error(
                        FilterValidationResult.Error.Code.ARGUMENT_RULE, -1, null, violation));
        thrownBy(NullPointerException.class, () -> FilterValidationResult.Error.conversionFailed(0, null));
        thrownBy(NullPointerException.class, () ->
                new FilterValidationResult.Error(
                        FilterValidationResult.Error.Code.CONVERSION_FAILED, null, null, null));
        thrownBy(NullPointerException.class, () ->
                new FilterValidationResult.Error(
                        FilterValidationResult.Error.Code.ARGUMENTS_RULE, null, null, null));
    }

    @Test
    void ruleViolationCanReplaceOrPrefixPaths() {
        RuleViolation blank = new RuleViolation("", "message", "{message}", "Constraint");
        RuleViolation nested = new RuleViolation("name", "message", "{message}", "Constraint");

        assertEquals("field", blank.prefixed("field").path());
        assertEquals("field.name", nested.prefixed("field").path());
        assertEquals("other", nested.withPath("other").path());
        thrownBy(NullPointerException.class, () -> nested.prefixed(null));
        thrownBy(NullPointerException.class, () -> new RuleViolation(null, "message", "{message}", "Constraint"));
    }

    @Test
    void rsqlOperatorArityValidatesRangesAndAcceptance() {
        RsqlOperatorArity exact = RsqlOperatorArity.exact(2);
        RsqlOperatorArity bounded = RsqlOperatorArity.between(1, 3);
        RsqlOperatorArity unbounded = RsqlOperatorArity.atLeast(1);

        assertTrue(exact.accepts(2));
        assertFalse(exact.accepts(1));
        assertFalse(exact.accepts(3));
        assertFalse(bounded.accepts(0));
        assertFalse(bounded.accepts(4));
        assertTrue(bounded.accepts(3));
        assertTrue(unbounded.accepts(100));
        thrownBy(IllegalArgumentException.class, () -> RsqlOperatorArity.exact(-1));
        thrownBy(IllegalArgumentException.class, () -> RsqlOperatorArity.between(2, 1));
    }

    @Test
    void rsqlOperatorDescriptorsAndRegistryValidateSymbols() {
        RsqlOperator custom = RsqlOperator.of("custom");
        RsqlOperator other = RsqlOperator.of("other");
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.builder(custom)
                .symbols("=custom=", "=c=")
                .exactArguments(2)
                .argumentType(String.class)
                .build();
        RsqlOperatorRegistry registry = new RsqlOperatorRegistry(List.of(descriptor));
        RsqlJpaOperatorBinding binding = new RsqlJpaOperatorBinding(custom, context -> null);
        RsqlJpaOperatorRegistry jpaRegistry = new RsqlJpaOperatorRegistry(List.of(binding));

        assertEquals(custom, descriptor.operator());
        assertEquals("=custom=", descriptor.symbol());
        assertEquals(Set.of("=custom=", "=c="), descriptor.symbols());
        assertEquals(String.class, descriptor.argumentType().orElseThrow());
        assertTrue(jpaRegistry.predicate(custom).isPresent());
        assertFalse(jpaRegistry.predicate(other).isPresent());
        assertEquals(descriptor, registry.require(custom));
        assertEquals(List.of(descriptor), List.copyOf(registry.descriptors()));
        assertTrue(registry.descriptor(descriptor.comparisonOperator()).isPresent());
        assertTrue(registry.descriptor(other).isEmpty());
        thrownBy(IllegalArgumentException.class, () -> registry.require(other));
        thrownBy(IllegalArgumentException.class, () -> RsqlOperatorDescriptor.builder(custom).build());
        thrownBy(IllegalArgumentException.class, () -> new RsqlOperatorRegistry(List.of(descriptor, descriptor)));
        thrownBy(IllegalArgumentException.class, () -> new RsqlJpaOperatorRegistry(List.of(binding, binding)));
        thrownBy(IllegalArgumentException.class, () -> new RsqlOperatorRegistry(List.of(
                descriptor,
                RsqlOperatorDescriptor.of(other, "=custom="))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rsqlJpaPredicateContextRequiresAttributeAndCopiesArguments() {
        CriteriaBuilder criteriaBuilder = proxy(CriteriaBuilder.class);
        Path<Object> path = proxy(Path.class);
        Attribute<Object, Object> attribute = proxy(Attribute.class);
        From<Object, Object> root = proxy(From.class);
        List<Object> arguments = new ArrayList<>(List.of("active"));

        RsqlJpaPredicateContext<Object, Object, Object, Object, Object> context =
                new RsqlJpaPredicateContext<>(criteriaBuilder, path, attribute, arguments, root, EQUAL);

        arguments.clear();

        assertSame(attribute, context.attribute());
        assertEquals("active", context.argument(0));
        assertEquals(List.of("active"), context.arguments());
        thrownBy(NullPointerException.class, () ->
                new RsqlJpaPredicateContext<>(criteriaBuilder, path, null, List.of("active"), root, EQUAL));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (instance, method, args) -> {
            if ("equals".equals(method.getName())) {
                return instance == args[0];
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(instance);
            }
            if ("toString".equals(method.getName())) {
                return type.getSimpleName() + "Proxy";
            }
            return null;
        });
    }
}
