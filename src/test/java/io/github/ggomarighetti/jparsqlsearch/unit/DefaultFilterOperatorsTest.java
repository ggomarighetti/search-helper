package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.filter.DefaultFilterOperators;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.BETWEEN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IS_NULL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_NULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultFilterOperatorsTest {
    @Test
    void definesRestrictiveProfilesForCommonJavaAndSpringTypes() {
        assertContains(String.class, EQUAL, NOT_EQUAL, IN, LIKE, IGNORE_CASE_LIKE);
        assertContains(BigDecimal.class, EQUAL, IN, GREATER_THAN_OR_EQUAL, BETWEEN);
        assertContains(int.class, EQUAL, IN, GREATER_THAN_OR_EQUAL, BETWEEN);
        assertContains(char.class, EQUAL, NOT_EQUAL, IN);
        assertContains(LocalDate.class, EQUAL, IN, GREATER_THAN_OR_EQUAL, BETWEEN);
        assertContains(DataSize.class, EQUAL, IN, GREATER_THAN_OR_EQUAL, BETWEEN);
        assertEquals(Set.of(EQUAL, NOT_EQUAL), DefaultFilterOperators.forType(Boolean.class));
        assertEquals(
                Set.of(EQUAL, NOT_EQUAL, IN, io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_IN),
                DefaultFilterOperators.forType(UUID.class));
        assertEquals(
                Set.of(EQUAL, NOT_EQUAL, IN, io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_IN),
                DefaultFilterOperators.forType(TestTypes.Status.class));
    }

    @Test
    void excludesNullChecksFromEveryDefaultProfile() {
        for (Class<?> type : Set.of(
                String.class,
                Boolean.class,
                BigDecimal.class,
                LocalDate.class,
                UUID.class,
                TestTypes.Status.class,
                DataSize.class)) {
            Set<RsqlOperator> operators = DefaultFilterOperators.forType(type);
            assertFalse(operators.contains(IS_NULL), type.getName());
            assertFalse(operators.contains(NOT_NULL), type.getName());
        }
    }

    @Test
    void filteringWithoutCustomizerUsesTheFieldTypeProfile() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("name", String.class).filterable();
                    fields.add("price", BigDecimal.class).filterable();
                    fields.add("status", TestTypes.Status.class).filterable();
                })
                .build();

        assertTrue(definition.field("name").orElseThrow().filtering().allows(LIKE));
        assertFalse(definition.field("name").orElseThrow().filtering().allows(BETWEEN));
        assertTrue(definition.field("price").orElseThrow().filtering().allows(BETWEEN));
        assertFalse(definition.field("status").orElseThrow().filtering().allows(LIKE));
    }

    @Test
    void filteringWithDefaultsCanAddOperatorsWithoutReplacingTheProfile() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class)
                        .filterable(filter -> filter.withDefaults().allow(IS_NULL)))
                .build();

        var filtering = definition.field("name").orElseThrow().filtering();

        assertTrue(filtering.allows(EQUAL));
        assertTrue(filtering.allows(LIKE));
        assertTrue(filtering.allows(IS_NULL));
    }

    @Test
    void filteringWithDefaultsCanDenyOperatorsFromTheProfile() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class)
                        .filterable(filter -> filter.withDefaults().deny(EQUAL)))
                .build();

        var filtering = definition.field("name").orElseThrow().filtering();

        assertFalse(filtering.allows(EQUAL));
        assertTrue(filtering.allows(NOT_EQUAL));
        assertTrue(filtering.allows(LIKE));
    }

    @Test
    void filteringWithDefaultsCannotDenyTheEntireProfile() {
        var builder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("active", Boolean.class)
                        .filterable(filter -> filter.withDefaults().deny(EQUAL, NOT_EQUAL)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                builder::build);

        assertEquals(
                "selector 'active' filtering must declare at least one allowed operator",
                exception.getMessage());
    }

    @Test
    void explicitAllowCanRestoreAnOperatorDeniedFromDefaults() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class)
                        .filterable(filter -> filter
                                .withDefaults()
                                .deny(EQUAL)
                                .allow(EQUAL)))
                .build();

        assertTrue(definition.field("name").orElseThrow().filtering().allows(EQUAL));
    }

    @Test
    void manualFilteringCustomizerStillDefinesAReplacementWhitelist() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("name", String.class)
                        .filterable(filter -> filter.allow(IS_NULL)))
                .build();

        var filtering = definition.field("name").orElseThrow().filtering();

        assertTrue(filtering.allows(IS_NULL));
        assertFalse(filtering.allows(EQUAL));
        assertFalse(filtering.allows(LIKE));
    }

    @Test
    void reportsUnsupportedDefaultProfilesAsDefinitionErrors() {
        var builder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("owner", TestTypes.Owner.class).filterable());

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                builder::build);

        assertEquals(
                SearchDefinitionValidationException.DEFAULT_OPERATORS_UNSUPPORTED_TYPE,
                exception.code());
    }

    @Test
    void filteringWithDefaultsReportsUnsupportedProfilesAsDefinitionErrors() {
        var builder = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("owner", TestTypes.Owner.class)
                        .filterable(filter -> filter.withDefaults().allow(IS_NULL)));

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                builder::build);

        assertEquals(
                SearchDefinitionValidationException.DEFAULT_OPERATORS_UNSUPPORTED_TYPE,
                exception.code());
    }

    @Test
    void returnedProfilesAreImmutable() {
        Set<RsqlOperator> operators = DefaultFilterOperators.forType(String.class);

        assertThrows(UnsupportedOperationException.class, operators::clear);
    }

    @Test
    void reportsWhetherDefaultProfilesExist() {
        assertTrue(DefaultFilterOperators.supports(String.class));
        assertFalse(DefaultFilterOperators.supports(TestTypes.Owner.class));
    }

    private static void assertContains(Class<?> type, RsqlOperator... expected) {
        assertTrue(DefaultFilterOperators.forType(type).containsAll(Set.of(expected)), type.getName());
    }
}
