package io.github.ggomarighetti.jparsqlsearch.autoconfigure;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlEngines;
import io.github.ggomarighetti.jparsqlsearch.compile.RsqlSearchGuardTestAccess;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorArity;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngineBuilder;
import io.github.ggomarighetti.jparsqlsearch.definition.validation.SearchDefinitionValidator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.PageRequest;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaRsqlSearchRsqlAutoConfigurationTest {
    private static final RsqlOperator STARTS_WITH = RsqlOperator.of("STARTS_WITH");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JpaRsqlSearchRsqlAutoConfiguration.class,
                    JpaRsqlSearchAutoConfiguration.class));

    @Test
    void createsCompilerWithoutExposingSpecializedGuardBeans() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(SearchCompiler.class));
            assertTrue(context.getBeansOfType(Object.class).values().stream()
                    .noneMatch(RsqlSearchGuardTestAccess::isRsqlSearchGuard));
        });
    }

    @Test
    void exposesOnlyConversionServiceAsArgumentConversionContract() {
        assertFalse(Arrays.stream(SearchRsqlEngineBuilder.class.getMethods())
                .anyMatch(method -> method.getName().equals("argumentConverter")));
    }

    @Test
    void usesConverterBeansWhenNoConversionServiceBeanExists() {
        contextRunner.withBean(SkuConverter.class).run(context -> {
            SearchCompiler compiler = context.getBean(SearchCompiler.class);
            SearchRsqlEngine engine = context.getBean(SearchRsqlEngine.class);
            SearchDefinition<CatalogItem> definition = SearchDefinition.builder().entity(CatalogItem.class)
                    .fields(fields -> fields.add("sku", Sku.class)
                            .filterable(filter -> filter.allow(EQUAL)))
                    .paging()
                    .build();

            compiler.compile("sku==SKU123", null, PageRequest.of(0, 10), definition);
            assertEquals(new Sku("SKU123"), engine.conversionService().convert("SKU123", Sku.class));

            PageRequest pageRequest = PageRequest.of(0, 10);
            RsqlFilterValidationException exception =
                    assertThrows(RsqlFilterValidationException.class, () ->
                            compiler.compile("sku==BAD", null, pageRequest, definition));

            assertEquals(RsqlFilterValidationException.RULES_FORBIDDEN, exception.code());
        });
    }

    @Test
    void appliesEngineCustomizersToDefaultEngine() {
        contextRunner.withBean(SearchRsqlEngineCustomizer.class, () -> builder -> builder.operator(
                RsqlOperatorDescriptor.builder(STARTS_WITH)
                        .symbol("=startsWith=")
                        .arity(RsqlOperatorArity.exact(1))
                        .argumentType(String.class)
                        .build(),
                context -> context.criteriaBuilder().like(
                        context.path().as(String.class),
                        context.argument(0) + "%")))
                .run(context -> {
                    SearchCompiler compiler = context.getBean(SearchCompiler.class);
                    SearchDefinition<CatalogTextEntry> definition = SearchDefinition.builder().entity(CatalogTextEntry.class)
                            .fields(fields -> fields.add("code", String.class)
                                    .filterable(filter -> filter.allow(STARTS_WITH)))
                            .paging()
                            .build();

                    assertNotNull(compiler.compile(
                            "code=startsWith=CAT",
                            null,
                            PageRequest.of(0, 10),
                            definition).specification());
                });
    }

    @Test
    void userProvidedEngineReplacesDefaultAndDoesNotReceiveCustomizers() {
        RsqlOperator ignored = RsqlOperator.of("IGNORED");
        SearchRsqlEngine userEngine = PerplexhubRsqlEngines.defaults();

        contextRunner
                .withBean(SearchRsqlEngine.class, () -> userEngine)
                .withBean(SearchRsqlEngineCustomizer.class, () -> builder -> builder.operator(
                        RsqlOperatorDescriptor.of(ignored, "=ignored=")))
                .run(context -> {
                    SearchRsqlEngine engine = context.getBean(SearchRsqlEngine.class);

                    assertEquals(userEngine, engine);
                    assertFalse(engine.operators().descriptor(ignored).isPresent());
                });
    }

    @Test
    void userProvidedBackendReplacesDefaultBackend() {
        AtomicBoolean compiled = new AtomicBoolean();
        AtomicReference<ConversionService> backendConversionService = new AtomicReference<>();
        RsqlBackendAdapter backend = new RsqlBackendAdapter() {
            @Override
            public <T> org.springframework.data.jpa.domain.Specification<T> compile(
                    RsqlCompilationRequest<T> request) {
                compiled.set(true);
                backendConversionService.set(request.conversionService());
                return org.springframework.data.jpa.domain.Specification.unrestricted();
            }
        };

        contextRunner.withBean(RsqlBackendAdapter.class, () -> backend).run(context -> {
            SearchCompiler compiler = context.getBean(SearchCompiler.class);
            SearchDefinition<CatalogTextEntry> definition = SearchDefinition.builder().entity(CatalogTextEntry.class)
                    .fields(fields -> fields.add("code", String.class)
                            .filterable(filter -> filter.allow(EQUAL)))
                    .paging()
                    .build();

            compiler.compile("code==CAT123", null, PageRequest.of(0, 10), definition);

            assertTrue(compiled.get());
            assertSame(
                    context.getBean(SearchRsqlEngine.class).conversionService(),
                    backendConversionService.get());
        });
    }

    @Test
    void injectsRuntimeDefinitionValidatorsIntoGuard() {
        contextRunner.withBean(SearchDefinitionValidator.class, () -> definition -> {
            throw new SearchDefinitionValidationException(
                    SearchDefinitionValidationException.RSQL_CONFIGURATION_INVALID,
                    "runtime validator executed");
        }).run(context -> {
            SearchCompiler compiler = context.getBean(SearchCompiler.class);
            SearchDefinition<CatalogTextEntry> definition = SearchDefinition.builder().entity(CatalogTextEntry.class)
                    .fields(fields -> fields.add("code", String.class)
                            .filterable(filter -> filter.allow(EQUAL)))
                    .paging()
                    .build();

            PageRequest pageRequest = PageRequest.of(0, 10);
            SearchDefinitionValidationException exception = assertThrows(
                    SearchDefinitionValidationException.class,
                    () -> compiler.compile(
                            "code==CAT123",
                            null,
                            pageRequest,
                            definition));

            assertEquals(SearchDefinitionValidationException.RSQL_CONFIGURATION_INVALID, exception.code());
        });
    }

    @Test
    void usesUniqueConversionServiceBeansForRsqlJpaExecution() {
        contextRunner.withBean(ConversionService.class, () -> {
            ApplicationConversionService conversionService = new ApplicationConversionService();
            conversionService.addConverter(String.class, CatalogCode.class, source -> {
                if (!source.startsWith("CAT")) {
                    throw new IllegalArgumentException("catalog code must start with CAT");
                }
                return new CatalogCode(source);
            });
            return conversionService;
        }).run(context -> {
            SearchCompiler compiler = context.getBean(SearchCompiler.class);
            SearchRsqlEngine engine = context.getBean(SearchRsqlEngine.class);
            SearchDefinition<CatalogEntry> definition = SearchDefinition.builder().entity(CatalogEntry.class)
                    .fields(fields -> fields.add("code", CatalogCode.class)
                            .filterable(filter -> filter.allow(EQUAL)))
                    .paging()
                    .build();

            compiler.compile("code==CAT123", null, PageRequest.of(0, 10), definition);
            assertEquals(new CatalogCode("CAT123"),
                    engine.conversionService().convert("CAT123", CatalogCode.class));

            PageRequest pageRequest = PageRequest.of(0, 10);
            RsqlFilterValidationException exception = assertThrows(
                    RsqlFilterValidationException.class,
                    () -> compiler.compile(
                            "code==BAD",
                            null,
                            pageRequest,
                            definition));

            assertEquals(RsqlFilterValidationException.RULES_FORBIDDEN, exception.code());
        });
    }

    private record Sku(String value) {}

    private record CatalogCode(String value) {}

    private static final class CatalogItem {
        public Sku getSku() {
            return null;
        }
    }

    private static final class CatalogEntry {
        public CatalogCode getCode() {
            return null;
        }
    }

    private static final class CatalogTextEntry {
        public String getCode() {
            return null;
        }
    }

    private static final class SkuConverter implements Converter<String, Sku> {
        @Override
        public Sku convert(String source) {
            if (!source.startsWith("SKU")) {
                throw new IllegalArgumentException("SKU must start with SKU");
            }
            return new Sku(source);
        }
    }
}
