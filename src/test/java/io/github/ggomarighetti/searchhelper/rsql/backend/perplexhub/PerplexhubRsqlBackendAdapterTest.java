package io.github.ggomarighetti.searchhelper.rsql.backend.perplexhub;

import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.rsql.RsqlAst;
import io.github.ggomarighetti.searchhelper.rsql.RsqlCompilationRequest;
import io.github.ggomarighetti.searchhelper.rsql.SearchRsqlEngine;
import io.github.ggomarighetti.searchhelper.unit.TestTypes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import static io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperators.EQUAL;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerplexhubRsqlBackendAdapterTest {
    private final PerplexhubRsqlBackendAdapter adapter = new PerplexhubRsqlBackendAdapter();
    private final SearchRsqlEngine engine = SearchRsqlEngine.defaults();

    @Test
    void rejectsValidatedComparisonWhenSelectorIsMissingFromDefinition() {
        RsqlAst ast = engine.parse("missing==value");
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("missing==value", ast, definition);

        assertThrows(
                IllegalStateException.class,
                () -> adapter.compile(request).toPredicate(root(), query(), criteriaBuilder()));
    }

    @Test
    void rejectsUnsupportedValidatedAstNodes() throws Exception {
        RsqlAst ast = ast(new UnsupportedNode());
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        RsqlCompilationRequest<TestTypes.Product> request = request("email==value", ast, definition);

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.compile(request).toPredicate(root(), query(), criteriaBuilder()));
    }

    private RsqlCompilationRequest<TestTypes.Product> request(
            String rsql,
            RsqlAst ast,
            SearchDefinition<TestTypes.Product> definition) {
        return new RsqlCompilationRequest<>(
                rsql,
                ast,
                definition,
                false,
                ApplicationConversionService.getSharedInstance(),
                engine.operators());
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
