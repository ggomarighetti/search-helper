package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.unit.TestRsqlEngines;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.property.RsqlInputGenerator;
import io.github.ggomarighetti.jparsqlsearch.property.SearchPropertyFixtures;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class RsqlPropertyTest {
    private static final int RANDOM_TRIES = 2_500;

    @Test
    void arbitraryRsqlInputNeverEscapesUnexpectedThrowable() {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();
        RsqlInputGenerator generator = new RsqlInputGenerator(0xA08_001L);

        for (int i = 0; i < RANDOM_TRIES; i++) {
            String input = generator.nextInput();
            try {
                guard.specification(input, definition);
            } catch (Throwable throwable) {
                if (!SearchPropertyFixtures.isExpectedRsqlThrowable(throwable)) {
                    fail("Unexpected throwable for RSQL input [" + escaped(input) + "]", throwable);
                }
            }
        }
    }

    @Test
    void selectorsOutsideWhitelistNeverCompile() {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();
        RsqlInputGenerator generator = new RsqlInputGenerator(0xA08_002L);

        for (int i = 0; i < 250; i++) {
            String input = generator.comparisonWithUnknownSelector();
            Throwable throwable = assertThrows(
                    Throwable.class,
                    () -> guard.specification(input, definition),
                    "Unknown selector unexpectedly compiled: " + escaped(input));
            if (!SearchPropertyFixtures.isExpectedRsqlThrowable(throwable)) {
                fail("Unexpected throwable for unknown selector input [" + escaped(input) + "]", throwable);
            }
        }
    }

    @Test
    void disallowedOperatorsNeverCompile() {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();
        RsqlInputGenerator generator = new RsqlInputGenerator(0xA08_003L);

        for (int i = 0; i < 250; i++) {
            String input = generator.comparisonWithDisallowedOperator();
            Throwable throwable = assertThrows(
                    Throwable.class,
                    () -> guard.specification(input, definition),
                    "Disallowed operator unexpectedly compiled: " + escaped(input));
            if (!SearchPropertyFixtures.isExpectedRsqlThrowable(throwable)) {
                fail("Unexpected throwable for disallowed operator input [" + escaped(input) + "]", throwable);
            }
        }
    }

    @Test
    void exactRsqlLimitsAcceptNAndRejectNPlusOne() {
        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxLength("sku==A".length())))));
        assertLimitExceeded("sku==A",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxLength("sku==A".length() - 1))));

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "(((sku==A)))",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxParenthesesDepth(3)))));
        assertLimitExceeded("(((sku==A)))",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxParenthesesDepth(2))));

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A;name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxNodes(3)))));
        assertLimitExceeded("sku==A;name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxNodes(2))));

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A;name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxComparisons(2)))));
        assertProtectionExceeded("sku==A;name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxComparisons(1))),
                "filter.max-comparisons");

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A;(name==B,amount==10)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxDepth(3)))));
        assertLimitExceeded("sku==A;(name==B,amount==10)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxDepth(2))));

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A;name==B;amount==10",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxLogicalChildren(3)))));
        assertLimitExceeded("sku==A;name==B;amount==10",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.rsql(rsql -> rsql.maxLogicalChildren(2))));

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==A,name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxOrBranches(2)))));
        assertProtectionExceeded("sku==A,name==B",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxOrBranches(1))),
                "filter.max-or-branches");

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku=in=(A,B)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentsPerComparison(2)))));
        assertProtectionExceeded("sku=in=(A,B)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentsPerComparison(1))),
                "filter.max-arguments-per-comparison");

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku=in=(A,B);name=in=(C,D)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentsTotal(4)))));
        assertProtectionExceeded("sku=in=(A,B);name=in=(C,D)",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentsTotal(3))),
                "filter.max-arguments-total");

        assertDoesNotThrow(() -> new RsqlSearchGuard(TestRsqlEngines.defaults()).specification(
                "sku==ABC",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentLength(3)))));
        assertProtectionExceeded("sku==ABC",
                SearchPropertyFixtures.rsqlDefinition(limits -> limits.filter(filter -> filter.maxArgumentLength(2))),
                "filter.max-argument-length");
    }

    @Test
    void typedConversionsEitherCompileOrReturnRulesForbidden() {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();

        List<String> valid = List.of(
                "amount==10.50",
                "stock==10",
                "status==PUBLISHED",
                "releaseDate==2026-06-12");
        for (String input : valid) {
            assertDoesNotThrow(() -> guard.specification(input, definition), input);
        }

        List<String> invalid = List.of(
                "amount==not-a-number",
                "stock==10.50",
                "status==DELETED",
                "releaseDate==not-a-date");
        for (String input : invalid) {
            RsqlFilterValidationException exception =
                    assertThrows(RsqlFilterValidationException.class, () -> guard.specification(input, definition), input);
            assertEquals(RsqlFilterValidationException.RULES_FORBIDDEN, exception.code());
        }
    }

    @Test
    void deferredSpecificationFailuresAreAlwaysWrapped() {
        RsqlBackendAdapter throwingBackend = new RsqlBackendAdapter() {
            @Override
            public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
                return (root, query, criteriaBuilder) -> {
                    throw new IllegalStateException("deferred");
                };
            }
        };
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.builder()
                .backend(throwingBackend)
                .build());
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();

        for (String input : List.of("sku==A", "name==B", "amount==10.50", "stock=in=(1,2,3)")) {
            Specification<Product> specification = guard.specification(input, definition);
            RsqlFilterValidationException exception =
                    assertThrows(RsqlFilterValidationException.class, () -> specification.toPredicate(null, null, null));
            assertEquals(RsqlFilterValidationException.RULES_FORBIDDEN, exception.code());
        }
    }

    private static void assertLimitExceeded(String input, SearchDefinition<Product> definition) {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        RsqlFilterValidationException exception =
                assertThrows(RsqlFilterValidationException.class, () -> guard.specification(input, definition));
        assertEquals(RsqlFilterValidationException.LIMIT_EXCEEDED, exception.code(), input);
    }

    private static void assertProtectionExceeded(
            String input,
            SearchDefinition<Product> definition,
            String expectedRule) {
        RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());
        SearchProtectionException exception =
                assertThrows(SearchProtectionException.class, () -> guard.specification(input, definition));
        assertEquals(SearchProtectionException.PROTECTION_RULE_EXCEEDED, exception.code(), input);
        assertEquals(expectedRule, exception.rule(), input);
    }

    private static String escaped(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
