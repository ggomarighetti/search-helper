package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.unit.TestRsqlEngines;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlValidationError;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterOperator;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorArity;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsqlSearchGuardTest {
    private final RsqlSearchGuard guard = new RsqlSearchGuard(TestRsqlEngines.defaults());

    @Test
    void exposesConfiguredEngineAndPolicy() {
        RsqlSearchGuard conversionGuard =
                new RsqlSearchGuard(TestRsqlEngines.builder().conversionService(ApplicationConversionService.getSharedInstance()).build());
        SearchRsqlEngine engine = TestRsqlEngines.defaults();
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxComparisons(3))
                .build();
        RsqlSearchGuard configuredGuard = new RsqlSearchGuard(engine, policy);

        assertNotNull(conversionGuard.engine());
        assertEquals(SearchPolicy.defaults(), conversionGuard.policy());
        assertEquals(engine, configuredGuard.engine());
        assertEquals(policy, configuredGuard.policy());
    }

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
                TestRsqlEngines.builder()
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
    void wrapsImmediateBackendCompilationFailures() {
        RsqlBackendAdapter throwingBackend = new RsqlBackendAdapter() {
            @Override
            public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
                throw new IllegalStateException("boom");
            }
        };
        RsqlSearchGuard throwingGuard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
                        .conversionService(ApplicationConversionService.getSharedInstance())
                        .backend(throwingBackend)
                        .build(),
                SearchPolicy.defaults());
        SearchDefinition<TestTypes.Product> definition = filters();

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> throwingGuard.specification("taxId==20123456789", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
    }

    @Test
    void preservesDeferredRsqlFilterValidationExceptions() {
        RsqlFilterValidationException expected = new RsqlFilterValidationException(
                RsqlFilterValidationException.RULES_FORBIDDEN,
                "already guarded");
        RsqlBackendAdapter throwingBackend = new RsqlBackendAdapter() {
            @Override
            public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
                return (root, query, criteriaBuilder) -> {
                    throw expected;
                };
            }
        };
        RsqlSearchGuard throwingGuard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
                        .conversionService(ApplicationConversionService.getSharedInstance())
                        .backend(throwingBackend)
                        .build(),
                SearchPolicy.defaults());

        Specification<TestTypes.Product> specification = throwingGuard.specification("taxId==20123456789", filters());

        assertEquals(expected, assertThrows(
                RsqlFilterValidationException.class,
                () -> specification.toPredicate(null, null, null)));
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
                TestRsqlEngines.builder()
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

        distinctCapturingGuard.specification("amount==10.50;reviewRating==5", definition);
        assertTrue(distinct.get());
    }

    @Test
    void rejectsAllowedOperatorsThatTheDefaultJpaAdapterCannotExecute() {
        RsqlOperator customOperator = RsqlOperator.of("CUSTOM");
        RsqlSearchGuard customGuard = new RsqlSearchGuard(
                TestRsqlEngines.builder()
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
                TestRsqlEngines.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=catalogCode=")
                                .argumentType(CatalogCode.class)
                                .build(),
                                context -> context.criteriaBuilder().conjunction())
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
                TestRsqlEngines.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=catalogCode=")
                                .argumentType(CatalogCode.class)
                                .build(),
                                context -> context.criteriaBuilder().conjunction())
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
                TestRsqlEngines.builder()
                        .conversionService(conversionService)
                        .operator(RsqlOperatorDescriptor.builder(customOperator)
                                .symbol("=brokenCode=")
                                .argumentType(CatalogCode.class)
                                .build(),
                                context -> context.criteriaBuilder().conjunction())
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
        var symbols = descriptor.symbols();
        assertThrows(UnsupportedOperationException.class, symbols::clear);
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
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("passwordHash==abc", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(1, exception.errors().size());
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.FIELD_NOT_ALLOWED, error.code());
        assertEquals("$.selector", error.astPath());
        assertEquals("passwordHash", error.selector());
    }

    @Test
    void rejectsOperatorNotAllowedForField() {
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId!=20123456789", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.OPERATOR_NOT_ALLOWED, error.code());
        assertEquals("$.operator", error.astPath());
        assertEquals("taxId", error.selector());
        assertEquals("NOT_EQUAL", error.operator());
    }

    @Test
    void rejectsDeclaredSelectorWhenFilteringIsDisabled() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class))
                .build();

        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("email==person@example.com", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        RsqlValidationError error = exception.errors().get(0);
        assertEquals(RsqlValidationError.FILTERING_DISABLED, error.code());
        assertEquals("$.selector", error.astPath());
        assertEquals("email", error.selector());
        assertEquals("EQUAL", error.operator());
    }

    @Test
    @SuppressWarnings("deprecation")
    void rejectsOperatorWithInvalidArity() throws ReflectiveOperationException {
        RsqlOperator pair = RsqlOperator.of("PAIR");
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.builder(pair)
                .symbol("=pair=")
                .arity(RsqlOperatorArity.exact(2))
                .argumentType(String.class)
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(pair, String.class, operator -> {})))
                .build();
        RsqlRulesValidator validator = validator(definition, new RsqlOperatorRegistry(List.of(descriptor)));

        List<RsqlValidationError> errors = validator.validate(ast(new ComparisonNode(
                new ComparisonOperator("=pair=", true),
                "email",
                List.of("person@example.com"))));

        RsqlValidationError error = errors.get(0);
        assertEquals(RsqlValidationError.OPERATOR_INVALID_ARITY, error.code());
        assertEquals("$.arguments", error.astPath());
        assertEquals("email", error.selector());
        assertEquals("PAIR", error.operator());
    }

    @Test
    void reportsUnsupportedAstNodeAndUnregisteredOperator() throws ReflectiveOperationException {
        RsqlRulesValidator validator = validator(filters(), TestRsqlEngines.defaults().operators());

        List<RsqlValidationError> unsupported = validator.validate(ast(new UnsupportedNode()));
        List<RsqlValidationError> unregistered = validator.validate(ast(new ComparisonNode(
                new ComparisonOperator("=ghost="),
                "taxId",
                List.of("20123456789"))));

        assertEquals(RsqlValidationError.FIELD_NOT_ALLOWED, unsupported.get(0).code());
        assertEquals("$", unsupported.get(0).astPath());
        assertEquals(RsqlValidationError.OPERATOR_NOT_ALLOWED, unregistered.get(0).code());
        assertEquals("$.operator", unregistered.get(0).astPath());
        assertEquals("taxId", unregistered.get(0).selector());
    }

    @Test
    void rejectsInvalidArgumentWithTypedValidator() {
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId==123", definition));

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
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId=in=(20123456789,123)", definition));

        assertValidationCode(exception, RsqlFilterValidationException.RULES_FORBIDDEN);
        assertEquals(1, exception.errors().size());
        assertEquals("$.arguments[1]", exception.errors().get(0).astPath());
        assertEquals(1, exception.errors().get(0).argumentIndex());
    }

    @Test
    void returnsRulesForbiddenWhenParsedFilterBreaksMultipleRules() {
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("passwordHash==abc;email!=not-an-email", definition));

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
        List<RsqlValidationError> errors = exception.errors();
        assertThrows(UnsupportedOperationException.class, errors::clear);
    }

    @Test
    void returnsParseViolationWhenRsqlCannotBeParsed() {
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception =
                assertThrows(RsqlFilterValidationException.class, () -> guard.specification("taxId==", definition));

        assertValidationCode(exception, RsqlFilterValidationException.PARSE_ERROR);
    }

    @Test
    void rejectsNullRsqlAsLimitViolation() {
        SearchDefinition<TestTypes.Product> definition = filters();
        RsqlFilterValidationException exception =
                assertThrows(RsqlFilterValidationException.class, () -> guard.specification(null, definition));

        assertValidationCode(exception, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsRsqlRawInputLongerThanLimit() {
        SearchDefinition<TestTypes.Product> definition = filters(limits -> limits
                .rsql(rsql -> rsql.maxLength(10)));
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("taxId==20123456789", definition));

        assertValidationCode(exception, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsRsqlParenthesesNestingBeforeParsing() {
        SearchDefinition<TestTypes.Product> definition = filters(limits -> limits
                .rsql(rsql -> rsql.maxParenthesesDepth(2)));
        RsqlFilterValidationException exception = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification("(((taxId==20123456789)))", definition));

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

        SearchDefinition<TestTypes.Product> nodesDefinition = filters(limits -> limits.rsql(rsql -> rsql.maxNodes(1)));
        RsqlFilterValidationException nodes = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter, nodesDefinition));
        SearchDefinition<TestTypes.Product> comparisonsDefinition =
                filters(limits -> limits.filter(value -> value.maxComparisons(1)));
        SearchProtectionException comparisons = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification(filter, comparisonsDefinition));
        SearchDefinition<TestTypes.Product> depthDefinition = filters(limits -> limits.rsql(rsql -> rsql.maxDepth(1)));
        RsqlFilterValidationException depth = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter, depthDefinition));
        SearchDefinition<TestTypes.Product> childrenDefinition =
                filters(limits -> limits.rsql(rsql -> rsql.maxLogicalChildren(1)));
        RsqlFilterValidationException children = assertThrows(
                RsqlFilterValidationException.class,
                () -> guard.specification(filter, childrenDefinition));

        assertValidationCode(nodes, RsqlFilterValidationException.LIMIT_EXCEEDED);
        assertProtectionRule(comparisons, "filter.max-comparisons");
        assertValidationCode(depth, RsqlFilterValidationException.LIMIT_EXCEEDED);
        assertValidationCode(children, RsqlFilterValidationException.LIMIT_EXCEEDED);
    }

    @Test
    void rejectsRsqlOrBranchLimit() {
        SearchDefinition<TestTypes.Product> definition = filters(limits -> limits
                .filter(filter -> filter.maxOrBranches(1)));
        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId==20123456789,email==person@example.com", definition));

        assertProtectionRule(exception, "filter.max-or-branches");
    }

    @Test
    void recordsNestedOrMetadataWithTerminalJoinRoots() {
        SearchDefinition<Product> definition = SearchDefinition.builder()
                .entity(Product.class)
                .fields(fields -> {
                    fields.add("categoryCode", String.class)
                            .path("category.code")
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("sku", String.class)
                            .filterable(filter -> filter.allow(EQUAL));
                })
                .build();
        RsqlRulesValidator validator = validator(definition, TestRsqlEngines.defaults().operators());

        List<RsqlValidationError> errors = validator.validate(TestRsqlEngines.defaults()
                .parse("(categoryCode==laptops;sku==ABC),(sku==DEF)"));

        assertEquals(List.of(), errors);
    }

    @Test
    void rsqlHelpersHandleUnsupportedNodesAndRootPaths() throws ReflectiveOperationException {
        Method requiresDistinct = RsqlSearchGuard.class.getDeclaredMethod(
                "requiresDistinct",
                Node.class,
                SearchDefinition.class);
        requiresDistinct.setAccessible(true);
        Method orMetadata = RsqlRulesValidator.class.getDeclaredMethod("orMetadata", OrNode.class);
        orMetadata.setAccessible(true);

        assertEquals(false, requiresDistinct.invoke(null, new UnsupportedNode(), filters()));
        assertEquals("category", RsqlOrAnalyzer.rootPath("category"));
        assertEquals("category", RsqlOrAnalyzer.rootPath("category.code"));
        assertNotNull(orMetadata.invoke(
                validator(filters(), TestRsqlEngines.defaults().operators()),
                new OrNode(List.of(new UnsupportedNode()))));
    }

    @Test
    void rejectsRsqlArgumentLimitsBeforeConversionAndRules() {
        SearchDefinition<TestTypes.Product> perComparisonDefinition = filters(limits -> limits
                .filter(filter -> filter.maxArgumentsPerComparison(1)));
        SearchProtectionException perComparison = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId=in=(20123456789,20987654321)", perComparisonDefinition));
        SearchDefinition<TestTypes.Product> totalDefinition =
                filters(limits -> limits.filter(filter -> filter.maxArgumentsTotal(3)));
        SearchProtectionException total = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification(
                        "taxId=in=(20123456789,20987654321);taxId=in=(30123456789,30987654321)",
                        totalDefinition));
        SearchDefinition<TestTypes.Product> lengthDefinition = filters(limits -> limits
                .filter(filter -> filter.maxArgumentLength(5)));
        SearchProtectionException length = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("email==person@example.com", lengthDefinition));

        assertProtectionRule(perComparison, "filter.max-arguments-per-comparison");
        assertProtectionRule(total, "filter.max-arguments-total");
        assertProtectionRule(length, "filter.max-argument-length");
    }

    @Test
    void appliesProtectionBeforeDisallowedOperatorErrorsForDeclaredSelectors() {
        SearchDefinition<TestTypes.Product> definition = filters(limits -> limits
                .filter(filter -> filter.maxArgumentLength(5)));

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId!=20123456789", definition));

        assertProtectionRule(exception, "filter.max-argument-length");
    }

    @Test
    void appliesProtectionBeforeArgumentValidationErrors() {
        SearchDefinition<TestTypes.Product> definition = filters(limits -> limits
                .filter(filter -> filter.maxArgumentLength(2)));

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> guard.specification("taxId==abc", definition));

        assertProtectionRule(exception, "filter.max-argument-length");
    }

    @Test
    void appliesProtectionBeforeInvalidArityErrors() {
        RsqlOperator pair = RsqlOperator.of("PAIR");
        RsqlOperatorDescriptor descriptor = RsqlOperatorDescriptor.builder(pair)
                .symbol("=pair=")
                .arity(RsqlOperatorArity.exact(2))
                .argumentType(String.class)
                .build();
        SearchPolicy policy = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentLength(5))
                .build();
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder(policy).entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(pair, String.class, operator -> {})))
                .build();
        RsqlRulesValidator validator = new RsqlRulesValidator(
                definition,
                ApplicationConversionService.getSharedInstance(),
                policy.rsql(),
                new SearchProtectionContext(policy, SearchProtectionContext.Mode.PAGE),
                new RsqlOperatorRegistry(List.of(descriptor)));
        RsqlAst comparison = astUnchecked(new ComparisonNode(
                new ComparisonOperator("=pair=", true),
                "email",
                List.of("person@example.com")));

        SearchProtectionException exception = assertThrows(
                SearchProtectionException.class,
                () -> validator.validate(comparison));

        assertProtectionRule(exception, "filter.max-argument-length");
    }

    @Test
    void unrelatedLocalOverridePreservesGlobalArgumentLimit() {
        SearchPolicy globalPolicy = SearchPolicy.builder()
                .filter(filter -> filter.maxArgumentsPerComparison(10))
                .build();
        RsqlSearchGuard limitedGuard =
                new RsqlSearchGuard(TestRsqlEngines.builder().conversionService(ApplicationConversionService.getSharedInstance()).build(), globalPolicy);
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

    private static RsqlRulesValidator validator(
            SearchDefinition<?> definition,
            RsqlOperatorRegistry operators) {
        return new RsqlRulesValidator(
                definition,
                ApplicationConversionService.getSharedInstance(),
                SearchPolicy.defaults().rsql(),
                new SearchProtectionContext(
                        SearchPolicy.defaults(), SearchProtectionContext.Mode.PAGE),
                operators);
    }

    private static RsqlAst ast(Node node) throws ReflectiveOperationException {
        Constructor<RsqlAst> constructor = RsqlAst.class.getDeclaredConstructor(Node.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(node, List.of());
    }

    private static RsqlAst astUnchecked(Node node) {
        try {
            return ast(node);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class UnsupportedNode implements Node {
        @Override
        public <R, A> R accept(RSQLVisitor<R, A> visitor, A param) {
            return null;
        }

        @Override
        public <R, A> R accept(RSQLVisitor<R, A> visitor) {
            return null;
        }
    }
}
