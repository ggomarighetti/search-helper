package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterValidationResult;
import io.github.ggomarighetti.jparsqlsearch.filter.SearchFiltering;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilteringTest {
    @Test
    void acceptsAllowedOperatorsAndRejectsMissingOperators() {
        SearchFiltering<String> filtering = SearchFiltering.<String>builder()
                .allow(EQUAL)
                .build(
                        TestTypes.Product.class,
                        "email",
                        String.class,
                        "email",
                        SearchPolicy.defaults().paths());

        assertTrue(filtering.accepts(EQUAL, List.of("person@example.com"), ApplicationConversionService.getSharedInstance()));
        assertFalse(filtering.accepts(IN, List.of("person@example.com"), ApplicationConversionService.getSharedInstance()));
        thrownBy(
                NullPointerException.class,
                () -> filtering.accepts(null, List.of("person@example.com"), ApplicationConversionService.getSharedInstance()));
        thrownBy(
                NullPointerException.class,
                () -> filtering.accepts(EQUAL, null, ApplicationConversionService.getSharedInstance()));
        thrownBy(
                NullPointerException.class,
                () -> filtering.accepts(EQUAL, List.of("person@example.com"), null));
    }

    @Test
    void rejectsDuplicateOperatorDeclarations() {
        SearchFiltering.Builder<String> implicitType = SearchFiltering.builder();
        implicitType.allow(EQUAL);
        SearchFiltering.Builder<String> explicitType = SearchFiltering.builder();
        explicitType.allow(EQUAL, String.class, operator -> {});

        assertNotNull(thrownBy(IllegalArgumentException.class, () -> implicitType.allow(EQUAL)));
        thrownBy(IllegalArgumentException.class, () -> explicitType.allow(EQUAL, String.class, operator -> {}));
    }

    @Test
    void reportsUnsupportedDefaultOperatorProfile() {
        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> SearchFiltering.<TestTypes.Person>builder()
                        .withDefaults()
                        .build(
                                TestTypes.Product.class,
                                "person",
                                TestTypes.Person.class,
                                "person",
                                SearchPolicy.defaults().paths()));

        assertEquals(SearchDefinitionValidationException.DEFAULT_OPERATORS_UNSUPPORTED_TYPE, exception.code());
    }

    @Test
    void canDenyOperatorsInheritedFromDefaults() {
        SearchFiltering<String> filtering = SearchFiltering.<String>builder()
                .withDefaults()
                .deny(EQUAL)
                .build(
                        TestTypes.Product.class,
                        "email",
                        String.class,
                        "email",
                        SearchPolicy.defaults().paths());

        assertFalse(filtering.allows(EQUAL));
        assertTrue(filtering.allows(IN));
    }

    @Test
    void validatesExplicitArgumentConversionFailures() {
        SearchFiltering<String> filtering = SearchFiltering.<String>builder()
                .allow(EQUAL, NoConversion.class, operator -> {})
                .build(
                        TestTypes.Product.class,
                        "email",
                        String.class,
                        "email",
                        SearchPolicy.defaults().paths());

        FilterValidationResult result = filtering.operators()
                .get(EQUAL)
                .validate(List.of("value"), ApplicationConversionService.getSharedInstance());

        assertFalse(result.accepted());
        assertEquals(NoConversion.class.getName(), result.errors().get(0).optionalTargetType().orElseThrow());
    }

    @Test
    void treatsIllegalArgumentConversionAsInputConversionFailure() {
        SearchFiltering<String> filtering = SearchFiltering.<String>builder()
                .allow(EQUAL, NoConversion.class, operator -> {})
                .build(
                        TestTypes.Product.class,
                        "email",
                        String.class,
                        "email",
                        SearchPolicy.defaults().paths());

        FilterValidationResult result = filtering.operators()
                .get(EQUAL)
                .validate(List.of("value"), new ThrowingConversionService());

        assertFalse(result.accepted());
        assertEquals(NoConversion.class.getName(), result.errors().get(0).optionalTargetType().orElseThrow());
    }

    @Test
    void terminalCollectionFilteringRequiresDistinct() {
        SearchFiltering<List> filtering = SearchFiltering.<List>builder()
                .allow(EQUAL)
                .build(
                        TestTypes.Product.class,
                        "tags",
                        List.class,
                        "tags",
                        SearchPolicy.defaults().paths());

        assertTrue(filtering.requiresDistinct());
    }

    private static final class NoConversion {
    }

    private static final class ThrowingConversionService implements ConversionService {
        @Override
        public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
            return true;
        }

        @Override
        public boolean canConvert(TypeDescriptor sourceType, TypeDescriptor targetType) {
            return true;
        }

        @Override
        public <T> T convert(Object source, Class<T> targetType) {
            throw new IllegalArgumentException("bad input");
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            throw new IllegalArgumentException("bad input");
        }
    }
}
