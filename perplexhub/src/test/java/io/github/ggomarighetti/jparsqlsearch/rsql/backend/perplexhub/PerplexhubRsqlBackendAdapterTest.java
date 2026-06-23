package io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlEngines;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerplexhubRsqlBackendAdapterTest {
    private final PerplexhubRsqlBackendAdapter adapter = new PerplexhubRsqlBackendAdapter();
    private final SearchRsqlEngine engine = PerplexhubRsqlEngines.defaults();

    @Test
    void rejectsValidatedComparisonWhenSelectorIsMissingFromDefinition() {
        RsqlAst ast = engine.parse("missing==value");
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("missing==value", ast, definition);

        assertNotNull(thrownBy(
                IllegalStateException.class,
                () -> adapter.compile(request).toPredicate(root(), query(), criteriaBuilder())));
    }

    @Test
    void rejectsUnsupportedValidatedAstNodes() throws Exception {
        RsqlAst ast = ast(new UnsupportedNode());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("email==value", ast, definition);

        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> adapter.compile(request).toPredicate(root(), query(), criteriaBuilder())));
    }

    @Test
    void preservesExistingDistinctWhenRequestDoesNotRequireDistinct() throws Exception {
        RsqlAst ast = ast(new UnsupportedNode());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("email==value", ast, definition, false);
        RecordingCriteriaQuery query = new RecordingCriteriaQuery();

        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> adapter.compile(request).toPredicate(root(), query.proxy(), criteriaBuilder())));

        assertTrue(query.distinctValues.isEmpty());
    }

    @Test
    void enablesDistinctOnlyWhenRequestRequiresDistinct() throws Exception {
        RsqlAst ast = ast(new UnsupportedNode());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("email==value", ast, definition, true);
        RecordingCriteriaQuery query = new RecordingCriteriaQuery();

        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> adapter.compile(request).toPredicate(root(), query.proxy(), criteriaBuilder())));

        assertEquals(List.of(true), query.distinctValues);
    }

    private RsqlCompilationRequest<TestTypes.Product> request(
            String rsql,
            RsqlAst ast,
            SearchDefinition<TestTypes.Product> definition) {
        return request(rsql, ast, definition, false);
    }

    private RsqlCompilationRequest<TestTypes.Product> request(
            String rsql,
            RsqlAst ast,
            SearchDefinition<TestTypes.Product> definition,
            boolean distinct) {
        return new RsqlCompilationRequest<>(
                rsql,
                ast,
                definition,
                distinct,
                ApplicationConversionService.getSharedInstance(),
                engine.operators(),
                engine.jpaOperators());
    }

    private static RsqlAst ast(Node node) throws Exception {
        Constructor<RsqlAst> constructor = RsqlAst.class.getDeclaredConstructor(Node.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(node, List.of());
    }

    private static Root<TestTypes.Product> root() {
        return proxy(Root.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static CriteriaQuery<?> query() {
        return proxy(CriteriaQuery.class, (proxy, method, args) -> {
            if ("distinct".equals(method.getName())) {
                return proxy;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static CriteriaBuilder criteriaBuilder() {
        return proxy(CriteriaBuilder.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass().equals(Object.class)) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + " proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> defaultValue(method.getReturnType());
                        };
                    }
                    return handler.invoke(proxy, method, args);
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (void.class.equals(type)) {
            return null;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        return 0;
    }

    private static final class RecordingCriteriaQuery {
        private final List<Boolean> distinctValues = new ArrayList<>();

        private CriteriaQuery<?> proxy() {
            return PerplexhubRsqlBackendAdapterTest.proxy(CriteriaQuery.class, (proxy, method, args) -> {
                if ("distinct".equals(method.getName())) {
                    distinctValues.add((Boolean) args[0]);
                    return proxy;
                }
                return defaultValue(method.getReturnType());
            });
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
