package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.query.validation.SearchQueryValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.validator.cfg.defs.PatternDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.data.jpa.domain.Specification.unrestricted;

class SearchQueryGuardTest {
    private final SearchQueryGuard guard = new SearchQueryGuard();

    @Test
    void returnsUnrestrictedSpecificationWhenQueryIsBlank() {
        assertNotNull(guard.specification(null, definitionWithoutQuery()));
        assertNotNull(guard.specification("   ", definitionWithoutQuery()));
    }

    @Test
    void rejectsQueryWhenDefinitionDoesNotDeclareQuery() {
        SearchDefinition<TestTypes.Product> definition = definitionWithoutQuery();
        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tablets", definition));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    @Test
    void rejectsQueryWhenPolicyDisablesQuerying() {
        SearchQueryGuard disabledGuard = new SearchQueryGuard(SearchPolicy.builder()
                .query(query -> query.enabled(false))
                .build());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> unrestricted()))
                .build();

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> disabledGuard.specification("tablet", definition));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    @Test
    void rejectsQueryWhenPolicyRequiresRulesAndDefinitionHasNone() {
        SearchQueryGuard guarded = new SearchQueryGuard(SearchPolicy.builder()
                .query(query -> query.requireValidator(true))
                .build());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> unrestricted()))
                .build();

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> guarded.specification("tablet", definition));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    @Test
    void acceptsQueryWhenPolicyRequiresRulesAndDefinitionHasRules() {
        SearchQueryGuard guarded = new SearchQueryGuard(SearchPolicy.builder()
                .query(query -> query.requireValidator(true))
                .build());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> {
                    query.rule(new SizeDef().min(3));
                    query.specification(term -> unrestricted());
                })
                .build();

        assertNotNull(guarded.specification("tablet", definition));
    }

    @Test
    void rejectsQueryLongerThanSafetyLimitBeforeSpecificationFactoryRuns() {
        AtomicReference<String> captured = new AtomicReference<>();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .limits(limits -> limits.query(query -> query.maxLength(5)))
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> {
                    captured.set(term);
                    return unrestricted();
                }))
                .build();

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("tablet", definition));

        assertEquals(SearchProtectionException.PROTECTION_RULE_EXCEEDED, exception.code());
        assertEquals("query.max-length", exception.rule());
        assertEquals(null, captured.get());
    }

    @Test
    void validatesQueryWithHibernateRules() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> {
                    query.rule(new SizeDef().min(3).max(10));
                    query.rule(new PatternDef().regexp("[a-zA-Z0-9 ]+"));
                    query.specification(term -> unrestricted());
                })
                .build();

        assertNotNull(guard.specification("tablet 10", definition));

        SearchQueryValidationException sizeException = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tv", definition));
        SearchQueryValidationException patternException = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tablet!", definition));

        assertValidationCode(sizeException, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
        assertValidationCode(patternException, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
        assertEquals(1, sizeException.violations().size());
        assertEquals("query", sizeException.violations().get(0).path());
        assertEquals(
                "{jakarta.validation.constraints.Size.message}",
                sizeException.violations().get(0).messageTemplate());
        assertEquals(1, patternException.violations().size());
        assertEquals("query", patternException.violations().get(0).path());
        assertEquals(
                "{jakarta.validation.constraints.Pattern.message}",
                patternException.violations().get(0).messageTemplate());
        var violations = sizeException.violations();
        assertThrows(UnsupportedOperationException.class, violations::clear);
    }

    @Test
    void passesOriginalQueryStringToSpecification() {
        AtomicReference<String> captured = new AtomicReference<>();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> {
                    captured.set(term);
                    return unrestricted();
                }))
                .build();

        guard.specification("  Raw Value  ", definition);

        assertEquals("  Raw Value  ", captured.get());
    }

    @Test
    void rejectsQuerySpecificationThatReturnsNull() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> null))
                .build();

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tablet", definition));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    @Test
    void wrapsDeferredQuerySpecificationFailures() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> (root, criteria, builder) -> {
                    throw new IllegalStateException("boom");
                }))
                .build();

        var specification = guard.specification("tablet", definition);

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> specification.toPredicate(null, null, null));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    @Test
    void preservesDeferredQueryValidationFailures() {
        SearchQueryValidationException expected = new SearchQueryValidationException(
                SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                "query rejected");
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> (root, criteria, builder) -> {
                    throw expected;
                }))
                .build();

        var specification = guard.specification("tablet", definition);
        SearchQueryValidationException actual = assertThrows(
                SearchQueryValidationException.class,
                () -> specification.toPredicate(null, null, null));

        assertSame(expected, actual);
    }

    @Test
    void wrapsQuerySpecificationFailures() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .query(query -> query.specification(term -> {
                    throw new IllegalStateException("boom");
                }))
                .build();

        SearchQueryValidationException exception = assertThrows(
                SearchQueryValidationException.class,
                () -> guard.specification("tablet", definition));

        assertValidationCode(exception, SearchQueryValidationException.QUERY_RULES_FORBIDDEN);
    }

    private static SearchDefinition<TestTypes.Product> definitionWithoutQuery() {
        return SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class))
                .build();
    }

    private static void assertValidationCode(
            SearchQueryValidationException exception, String expectedCode) {
        assertEquals(expectedCode, exception.code());
    }
}
