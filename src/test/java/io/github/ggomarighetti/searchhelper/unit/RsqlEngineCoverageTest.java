package io.github.ggomarighetti.searchhelper.unit;

import cz.jirutka.rsql.parser.RSQLParser;
import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.searchhelper.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.searchhelper.rsql.SearchRsqlEngine;
import io.github.ggomarighetti.searchhelper.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.searchhelper.rsql.backend.perplexhub.PerplexhubRsqlBackendAdapter;
import io.github.ggomarighetti.searchhelper.rsql.backend.perplexhub.PerplexhubRsqlBackendOptions;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.searchhelper.rsql.operator.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperatorDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.jpa.domain.Specification;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.EQUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static io.github.ggomarighetti.searchhelper.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsqlEngineCoverageTest {
    private static final RsqlOperator CUSTOM = RsqlOperator.of("custom");

    @Test
    void builderAcceptsBulkOperatorsParserFactoryBackendAndConversionService() {
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.of(CUSTOM, "=custom=");
        RsqlBackendAdapter backend = new NoOpBackend();
        SearchRsqlEngine engine = SearchRsqlEngine.builder()
                .operators(List.of(descriptor))
                .parserFactory(operators -> new RSQLParser(operators.parserOperators()))
                .backend(backend)
                .conversionService(ApplicationConversionService.getSharedInstance())
                .build();

        assertTrue(engine.operators().descriptor(CUSTOM).isPresent());
        assertNotNull(engine.parse("email==value"));
        thrownBy(NullPointerException.class, () -> SearchRsqlEngine.builder().operators(null));
        thrownBy(NullPointerException.class, () -> SearchRsqlEngine.builder().parserFactory(null));
    }

    @Test
    void parsedAstExposesNormalizedComparisonsForLogicalExpressions() {
        var ast = SearchRsqlEngine.defaults().parse("email==a;name==b");

        assertEquals(2, ast.comparisons().size());
        assertEquals(EQUAL, ast.comparisons().get(0).operator());
        assertTrue(DefaultRsqlOperatorDescriptors.isDefault(EQUAL));
        assertFalse(DefaultRsqlOperatorDescriptors.isDefault(CUSTOM));
    }

    @Test
    void validateRejectsUnregisteredOperatorsAndTypeMismatches() {
        SearchDefinition<TestTypes.Product> unregistered = definition(CUSTOM, String.class);
        SearchDefinition<TestTypes.Product> mismatch = definition(CUSTOM, Integer.class);
        SearchRsqlEngine mismatchEngine = SearchRsqlEngine.builder()
                .operator(RsqlOperatorDescriptor.builder(CUSTOM)
                        .symbol("=custom=")
                        .argumentType(String.class)
                        .build())
                .backend(new NoOpBackend())
                .build();

        SearchDefinitionValidationException missing = thrownBy(
                SearchDefinitionValidationException.class,
                () -> SearchRsqlEngine.defaults().validate(unregistered));
        SearchDefinitionValidationException typeMismatch = thrownBy(
                SearchDefinitionValidationException.class,
                () -> mismatchEngine.validate(mismatch));

        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_NOT_REGISTERED, missing.code());
        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH, typeMismatch.code());
    }

    @Test
    void validateRejectsOperatorsThatCannotBeConvertedFromString() {
        SearchDefinition<TestTypes.Product> definition = definition(CUSTOM, NoConversion.class);
        SearchRsqlEngine engine = SearchRsqlEngine.builder()
                .operator(RsqlOperatorDescriptor.builder(CUSTOM)
                        .symbol("=custom=")
                        .argumentType(NoConversion.class)
                        .build())
                .backend(new NoOpBackend())
                .build();

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> engine.validate(definition));

        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH, exception.code());
    }

    @Test
    void validateAllowsStringArgumentTypesWithoutRegisteredStringConverter() {
        SearchDefinition<TestTypes.Product> definition = definition(CUSTOM, String.class);
        SearchRsqlEngine engine = SearchRsqlEngine.builder()
                .operator(RsqlOperatorDescriptor.builder(CUSTOM)
                        .symbol("=custom=")
                        .argumentType(String.class)
                        .build())
                .backend(new NoOpBackend())
                .conversionService(new NoStringConversionService())
                .build();

        engine.validate(definition);
    }

    @Test
    void perplexhubOptionsCustomizeAndBackendRequiresCustomArgumentTypes() {
        PerplexhubRsqlBackendOptions options = PerplexhubRsqlBackendOptions.builder()
                .customize(builder -> builder
                        .strictEquality(false)
                        .likeEscapeCharacter('\\'))
                .build();
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.builder(CUSTOM)
                .symbol("=custom=")
                .jpaPredicate(context -> null)
                .build();
        SearchRsqlEngine engine = SearchRsqlEngine.builder()
                .operator(descriptor)
                .backend(new NoOpBackend())
                .build();
        SearchDefinition<TestTypes.Product> definition = definition(CUSTOM, String.class);

        assertFalse(options.strictEquality());
        assertEquals('\\', options.likeEscapeCharacter());
        thrownBy(NullPointerException.class, () -> PerplexhubRsqlBackendOptions.builder().customize(null));

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> new PerplexhubRsqlBackendAdapter(options).validate(engine, definition));

        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH, exception.code());
    }

    private static <A> SearchDefinition<TestTypes.Product> definition(RsqlOperator operator, Class<A> argumentType) {
        return SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(operator, argumentType, customizer -> {})))
                .build();
    }

    private static final class NoConversion implements Comparable<NoConversion> {
        @Override
        public int compareTo(NoConversion other) {
            return 0;
        }
    }

    private static final class NoOpBackend implements RsqlBackendAdapter {
        @Override
        public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
    }

    private static final class NoStringConversionService implements ConversionService {
        @Override
        public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
            return false;
        }

        @Override
        public boolean canConvert(TypeDescriptor sourceType, TypeDescriptor targetType) {
            return false;
        }

        @Override
        public <T> T convert(Object source, Class<T> targetType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            throw new UnsupportedOperationException();
        }
    }
}
