package io.github.ggomarighetti.searchhelper.compile;

import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.exception.RsqlFilterValidationException;
import io.github.ggomarighetti.searchhelper.exception.RsqlValidationError;
import io.github.ggomarighetti.searchhelper.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.searchhelper.exception.SearchProtectionException;
import io.github.ggomarighetti.searchhelper.filter.FilterOperator;
import io.github.ggomarighetti.searchhelper.integration.bench.domain.Product;
import io.github.ggomarighetti.searchhelper.integration.bench.domain.Status;
import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import io.github.ggomarighetti.searchhelper.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperatorDescriptor;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators;
import io.github.ggomarighetti.searchhelper.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.searchhelper.unit.TestTypes;
import io.github.ggomarighetti.searchhelper.rsql.SearchRsqlEngine;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.hibernate.validator.cfg.defs.PatternDef;
import org.hibernate.validator.cfg.defs.PositiveDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.data.jpa.domain.Specification;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsqlSearchGuardTest {
    private final RsqlSearchGuard guard = new RsqlSearchGuard();

    @Test
    void returnsSpecificationForAllowedFieldOperatorAndArgument() {
        Specification<TestTypes.Product> specification = guard.specification("taxId==20123456789", filters());

        assertNotNull(specification);
    }

    @Test
    void wrapsDeferredSpecificationFailures() {
        RsqlBackendAdapter throwingBackend = new RsqlBackendAdapter() {
            @Override
            public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
                return (root, query, criteriaBuilder) -> {
                    throw new IllegalStateException("boom");
                };
            }
        };
        RsqlSearchGuard throwingGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .conversionService(ApplicationConversionService.getSharedInstance())
                        .backend(throwingBackend)
                        .build(),
                SearchPolicy.defaults());

        Specification<TestTypes.Product> specification = throwingGuard.specification("taxId==20123456789", filters());

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> specification.toPredicate(null, null, null));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
    }

    @Test
    void enablesDistinctOnlyWhenTheUsedFilterTraversesACollectionValuedPath() {
        AtomicReference<Boolean> distinct = new AtomicReference<>();
        RsqlBackendAdapter distinctCapturingBackend = new RsqlBackendAdapter() {
            @Override
            public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
                distinct.set(request.distinct());
                return Specification.unrestricted();
            }
        };
        RsqlSearchGuard distinctCapturingGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .conversionService(ApplicationConversionService.getSharedInstance())
                        .backend(distinctCapturingBackend)
                        .build(),
                SearchPolicy.defaults());

        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("amount", BigDecimal.class)
                            .path("price")
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("reviewRating", Integer.class)
                            .path("reviews.rating")
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();

        distinctCapturingGuard.specification("amount==10.50", definition);
        assertFalse(distinct.get());

        distinctCapturingGuard.specification("reviewRating==5", definition);
        assertTrue(distinct.get());
    }

    @Test
    void rejectsAllowedOperatorsThatTheDefaultJpaAdapterCannotExecute() {
        RsqlOperator customOperator = RsqlOperator.of("CUSTOM");
        RsqlSearchGuard customGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=custom=")
                                .build())
                        .build(),
                SearchPolicy.defaults());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(customOperator)))
                .build();

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                () -> customGuard.specification("email=custom=person@example.com", definition));

        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_NOT_EXECUTABLE, exception.code());
    }

    @Test
    void rejectsCustomOperatorWhenValidationAndExecutionArgumentTypesDiffer() {
        RsqlOperator customOperator = RsqlOperator.of("CATALOG_CODE");
        ApplicationConversionService conversionService = new ApplicationConversionService();
        conversionService.addConverter(String.class, CatalogCode.class, CatalogCode::new);
        RsqlSearchGuard customGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=catalogCode=")
                                .argumentType(CatalogCode.class)
                                .jpaPredicate(context -> context.criteriaBuilder().conjunction())
                                .build())
                        .build(),
                SearchPolicy.defaults());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(customOperator)))
                .build();

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                () -> customGuard.specification("email=catalogCode=CAT123", definition));

        assertEquals(SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH, exception.code());
    }

    @Test
    void validatesCustomOperatorArgumentsWithExplicitArgumentType() {
        RsqlOperator customOperator = RsqlOperator.of("CATALOG_CODE");
        ApplicationConversionService conversionService = new ApplicationConversionService();
        conversionService.addConverter(String.class, CatalogCode.class, CatalogCode::new);
        RsqlSearchGuard customGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=catalogCode=")
                                .argumentType(CatalogCode.class)
                                .jpaPredicate(context -> context.criteriaBuilder().conjunction())
                                .build())
                        .build(),
                SearchPolicy.defaults());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(customOperator, CatalogCode.class, operator -> {})))
                .build();

        assertNotNull(customGuard.specification("email=catalogCode=CAT123", definition));
    }

    @Test
    void reportsBrokenConvertersAsInvalidConfiguration() {
        RsqlOperator customOperator = RsqlOperator.of("BROKEN_CODE");
        ApplicationConversionService conversionService = new ApplicationConversionService();
        conversionService.addConverter(String.class, CatalogCode.class, source -> {
            throw new IllegalStateException("converter not initialized");
        });
        RsqlSearchGuard customGuard = new RsqlSearchGuard(
                SearchRsqlEngine.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=brokenCode=")
                                .argumentType(CatalogCode.class)
                                .jpaPredicate(context -> context.criteriaBuilder().conjunction())
                                .build())
                        .build(),
                SearchPolicy.defaults());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(customOperator, CatalogCode.class, operator -> {})))
                .build();

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                () -> customGuard.specification("email=brokenCode=CAT123", definition));

        assertEquals(SearchDefinitionValidationException.RSQL_CONFIGURATION_INVALID, exception.code());
    }

    @Test
    void keepsFirstDeclaredSymbolAsCanonicalSymbol() {
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.builder(RsqlOperator.of("ALIASED"))
                .symbols("=first=", "=second=")
                .build();

        assertEquals("=first=", descriptor.symbol());
        assertEquals(List.of("=first=", "=second="), descriptor.symbols().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.symbols().clear());
    }

    @Test
    void acceptsArgumentConvertibleToExplicitFieldType() {
        Specification<TestTypes.Product> specification = guard.specification("amount==10.50",
                SearchDefinition.builder().entity(TestTypes.Product.class)
                        .fields(fields -> fields.add("amount", BigDecimal.class)
                                .path("price")
                                .filterable(filter -> filter.allow(EQUAL)))
                        .build());

        assertNotNull(specification);
    }

    @Test
    void rejectsArgumentNotConvertibleToExplicitFieldType() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", BigDecimal.class)
                        .path("price")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("amount==abc", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(1, exception.errors().size());
        assertEquals(
                RsqlValidationError.ARGUMENT_CONVERSION_FAILED,
                exception.errors().get(0).code());
        assertEquals("$.arguments[0]", exception.errors().get(0).astPath());
        assertEquals(0, exception.errors().get(0).argumentIndex());
    }

    @Test
    void validatesConvertedArgumentWithTypedValidator() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("amount", BigDecimal.class)
                        .path("price")
                        .filterable(filter -> filter.allow(EQUAL, operator -> operator.each(each -> {
                            each.rule(new PositiveDef());
                        }))))
                .build();

        assertNotNull(guard.specification("amount==10.50", definition));

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("amount==-1", definition));
        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(
                RsqlValidationError.ARGUMENT_RULE_VIOLATION,
                exception.errors().get(0).code());
        assertTrue(exception.errors().get(0).constraint().endsWith(".Positive"));
    }

    @Test
    void validatesArgumentsWithOperatorRules() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("status", TestTypes.Status.class)
                        .filterable(filter -> filter.allow(IN, operator -> operator.args(args -> {
                            args.rule(new SizeDef().min(3));
                        }))))
                .build();

        assertNotNull(guard.specification("status=in=(ACTIVE,INACTIVE,PENDING)", definition));

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("status=in=(ACTIVE,INACTIVE)", definition));
        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(
                RsqlValidationError.ARGUMENTS_RULE_VIOLATION,
                exception.errors().get(0).code());
        assertEquals("$.arguments", exception.errors().get(0).astPath());
        assertTrue(exception.errors().get(0).constraint().endsWith(".Size"));
    }

    @Test
    void validatesEntityPathAgainstExplicitFieldType() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("amount", BigDecimal.class)
                            .path("price")
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("status", TestTypes.Status.class)
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();

        assertNotNull(guard.specification("amount==10.50", definition));
        assertNotNull(guard.specification("status==ACTIVE", definition));

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("status==DELETED", definition));
        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
    }

    @Test
    void rejectsUnknownField() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("passwordHash==abc", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(1, exception.errors().size());
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.FIELD_NOT_ALLOWED, error.code());
        assertEquals("$.selector", error.astPath());
        assertEquals("passwordHash", error.selector());
    }

    @Test
    void rejectsOperatorNotAllowedForField() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId!=20123456789", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.OPERATOR_NOT_ALLOWED, error.code());
        assertEquals("$.operator", error.astPath());
        assertEquals("taxId", error.selector());
        assertEquals("NOT_EQUAL", error.operator());
    }

    @Test
    void rejectsInvalidArgumentWithTypedValidator() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId==123", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.ARGUMENT_RULE_VIOLATION, error.code());
        assertEquals("$.arguments[0]", error.astPath());
        assertEquals(0, error.argumentIndex());
        assertTrue(error.constraint().endsWith(".Size"));
        assertFalse(error.message().isBlank());
        assertEquals("{jakarta.validation.constraints.Size.message}", error.messageTemplate());
    }

    @Test
    void rejectsAnyInvalidArgumentOfMultiValueOperators() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId=in=(20123456789,123)", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(1, exception.errors().size());
        assertEquals("$.arguments[1]", exception.errors().get(0).astPath());
        assertEquals(1, exception.errors().get(0).argumentIndex());
    }

    @Test
    void returnsRulesForbiddenWhenParsedFilterBreaksMultipleRules() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("passwordHash==abc;email!=not-an-email", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(2, exception.errors().size());
        assertEquals(
                List.of("$.children[0].selector", "$.children[1].operator"),
                exception.errors().stream().map(RsqlValidationError::astPath).toList());
        assertEquals(
                List.of(
                        RsqlValidationError.FIELD_NOT_ALLOWED,
                        RsqlValidationError.OPERATOR_NOT_ALLOWED),
                exception.errors().stream().map(RsqlValidationError::code).toList());
        assertThrows(UnsupportedOperationException.class, () -> exception.errors().clear());
    }

    @Test
    void returnsParseViolationWhenRsqlCannotBeParsed() {
        RsqlFilterValidationException exception =
                assertThrows(RsqlFilterValidationException.class, () -> guard.specification("taxId==", filters()));

        assertValidationCode(exception, RsqlFilterValidationException.PARSE_ERROR);
    }

    @Test
    void rejectsRsqlRawInputLongerThanLimit() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId==20123456789", filters(limits -> limits
                        .rsql(rsql -> rsql.maxLength(10)))));

        assertValidationCode(exception, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsRsqlParenthesesNestingBeforeParsing() {
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("(((taxId==20123456789)))", filters(limits -> limits
                        .rsql(rsql -> rsql.maxParenthesesDepth(2)))));

        assertValidationCode(exception, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void ignoresParenthesesInsideQuotedArgumentsBeforeParsing() {
        Specification<TestTypes.Product> specification = guard.specification(
                "email=='person(((example@example.com'",
                filters(limits -> limits.rsql(rsql -> rsql.maxParenthesesDepth(1))));

        assertNotNull(specification);
    }

    @Test
    void rejectsRsqlAstNodeComparisonDepthAndLogicalChildLimits() {
        String filter = "taxId==20123456789;email==person@example.com";

        RsqlFilterValidationException nodes = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter, filters(limits -> limits.rsql(rsql -> rsql.maxNodes(1)))));
        SearchProtectionException comparisons = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification(filter, filters(limits -> limits.filter(value -> value.maxComparisons(1)))));
        RsqlFilterValidationException depth = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter, filters(limits -> limits.rsql(rsql -> rsql.maxDepth(1)))));
        RsqlFilterValidationException children = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter,
                        filters(limits -> limits.rsql(rsql -> rsql.maxLogicalChildren(1)))));

        assertValidationCode(nodes, RsqlFilterValidationException.LIMIT_EXCEEDED);
        assertProtectionRule(comparisons, "filter.max-comparisons");
        assertValidationCode(depth, RsqlFilterValidationException.LIMIT_EXCEEDED);
        assertValidationCode(children, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsRsqlOrBranchLimit() {
        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId==20123456789,email==person@example.com", filters(limits -> limits
                        .filter(filter -> filter.maxOrBranches(1)))));

        assertProtectionRule(exception, "filter.max-or-branches");
    }

    @Test
    void rejectsRsqlArgumentLimitsBeforeConversionAndRules() {
        SearchProtectionException perComparison = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId=in=(20123456789,20987654321)", filters(limits -> limits
                        .filter(filter -> filter.maxArgumentsPerComparison(1)))));
        SearchProtectionException total = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification(
                        "taxId=in=(20123456789,20987654321);taxId=in=(30123456789,30987654321)",
                        filters(limits -> limits.filter(filter -> filter.maxArgumentsTotal(3)))));
        SearchProtectionException length = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("email==person@example.com", filters(limits -> limits
                        .filter(filter -> filter.maxArgumentLength(5)))));

        assertProtectionRule(perComparison, "filter.max-arguments-per-comparison");
        assertProtectionRule(total, "filter.max-arguments-total");
        assertProtectionRule(length, "filter.max-argument-length");
    }

    @Test
    void unrelatedLocalOverridePreservesGlobalArgumentLimit() {
        SearchPolicy globalPolicy = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentsPerComparison(10))
                .build();
        RsqlSearchGuard limitedGuard =
                new RsqlSearchGuard(ApplicationConversionService.getSharedInstance(), globalPolicy);
        SearchDefinition<TestTypes.Product> definition = filters(limits ->
                limits.paging(paging -> paging.maxSize(7)));

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> limitedGuard.specification(
                        "taxId=in=(20123456789,20234567890,20345678901,20456789012,20567890123,"
                                + "20678901234,20789012345,20890123456,20901234567,21012345678,21123456789)",
                        definition));

        assertProtectionRule(exception, "filter.max-arguments-per-comparison");
    }

    private static SearchDefinition<TestTypes.Product> filters() {
        return filters(null);
    }

    private static SearchDefinition<TestTypes.Product> filters(Consumer<SearchPolicy.Builder> limits) {
        SearchDefinition.Builder<TestTypes.Product> builder = SearchDefinition.builder().entity(TestTypes.Product.class);
        if (limits != null) {
            builder.limits(limits);
        }
        return builder
                .fields(fields -> {
                    fields.add("taxId", String.class)
                            .path("person.taxIdentifier")
                            .filterable(filter -> filter
                                    .allow(EQUAL, operator -> operator.each(RsqlSearchGuardTest::taxIdRules))
                                    .allow(IN, operator -> operator.each(RsqlSearchGuardTest::taxIdRules)));
                    fields.add("email", String.class)
                            .path("email")
                            .filterable(filter -> filter.allow(EQUAL, operator -> operator.each(each -> {
                                each.rule(new PatternDef().regexp(".+@.+"));
                            })));
                })
                .build();
    }

    private static void taxIdRules(FilterOperator.Rules<String> each) {
        each.rule(new SizeDef().min(11).max(11));
        each.rule(new PatternDef().regexp("\\d+"));
    }

    private record CatalogCode(String value) implements Comparable<CatalogCode> {
        @Override
        public int compareTo(CatalogCode other) {
            return value.compareTo(other.value);
        }
    }

    private static void assertValidationCode(
            RsqlFilterValidationException exception, String expectedCode) {
        assertEquals(expectedCode, exception.code());
    }

    private static void assertProtectionRule(SearchProtectionException exception, String expectedRule) {
        assertEquals(SearchProtectionException.PROTECTION_RULE_EXCEEDED, exception.code());
        assertEquals(expectedRule, exception.rule());
    }
}
