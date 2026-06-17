package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.unit.TestTypes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSpecificationSortingTest {
    @Test
    void detectsAndRemovesSortsThatNeedCriteriaSorting() {
        SearchDefinition<TestTypes.Product> definition = definition();
        Pageable pageable = PageRequest.of(2, 25, Sort.by("email", "expiresAt"));

        assertTrue(SearchSpecificationSorting.requiresCriteriaSorting(pageable, definition));
        assertFalse(SearchSpecificationSorting.requiresCriteriaSorting(
                PageRequest.of(2, 25, Sort.by("email")), definition));
        assertTrue(SearchSpecificationSorting.withoutSort(pageable).getSort().isUnsorted());
    }

    @Test
    void appliesTranslatedCriteriaOrdersToNonCountQueriesOnly() {
        SearchDefinition<TestTypes.Product> definition = definition();
        Predicate predicate = proxy(Predicate.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        Specification<TestTypes.Product> source = (root, query, builder) -> predicate;
        Sort sort = Sort.by(
                Sort.Order.desc("email").ignoreCase().nullsFirst(),
                Sort.Order.asc("expiresAt").nullsLast());
        RecordingCriteriaQuery<TestTypes.Product> contentQuery =
                new RecordingCriteriaQuery<>(TestTypes.Product.class);
        RecordingCriteriaBuilder builder = new RecordingCriteriaBuilder();
        Root<TestTypes.Product> root = namedRoot("root", builder);

        Predicate actual = SearchSpecificationSorting.apply(source, sort, definition)
                .toPredicate(root, contentQuery.proxy(), builder.proxy());

        assertSame(predicate, actual);
        assertEquals(List.of("root.email", "treated.expiresAt"), builder.pathReads);
        assertEquals(List.of(TestTypes.PerishableProduct.class), builder.treatedTypes);
        assertEquals(4, contentQuery.orders.size());
        assertEquals(List.of("asc", "desc", "asc", "asc"), builder.orderCalls);

        RecordingCriteriaQuery<Long> countQuery = new RecordingCriteriaQuery<>(Long.class);
        SearchSpecificationSorting.apply(source, sort, definition)
                .toPredicate(root, countQuery.proxy(), builder.proxy());

        assertTrue(countQuery.orders.isEmpty());

        RecordingCriteriaQuery<Long> primitiveCountQuery = new RecordingCriteriaQuery<>(long.class);
        SearchSpecificationSorting.apply(source, sort, definition)
                .toPredicate(root, primitiveCountQuery.proxy(), builder.proxy());

        assertTrue(primitiveCountQuery.orders.isEmpty());
    }

    @Test
    void appliesNativeNullHandlingAsSingleCriteriaOrder() {
        SearchDefinition<TestTypes.Product> definition = definition();
        Specification<TestTypes.Product> source = (root, query, builder) ->
                proxy(Predicate.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        RecordingCriteriaQuery<TestTypes.Product> contentQuery =
                new RecordingCriteriaQuery<>(TestTypes.Product.class);
        RecordingCriteriaBuilder builder = new RecordingCriteriaBuilder();

        SearchSpecificationSorting.apply(source, Sort.by(Sort.Order.asc("email")), definition)
                .toPredicate(namedRoot("root", builder), contentQuery.proxy(), builder.proxy());

        assertEquals(1, contentQuery.orders.size());
        assertEquals(List.of("asc"), builder.orderCalls);
    }

    private static SearchDefinition<TestTypes.Product> definition() {
        return SearchDefinition.builder().entity(TestTypes.Product.class)
                .fields(fields -> {
                    fields.add("email", String.class)
                            .sortable(sorting -> sorting.allowIgnoreCase().allowNullHandling(Sort.NullHandling.NULLS_FIRST));
                    fields.add("expiresAt", java.time.Instant.class)
                            .subtype(TestTypes.PerishableProduct.class)
                            .sortable(sorting -> sorting.allowNullHandling(Sort.NullHandling.NULLS_LAST));
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Root<TestTypes.Product> namedRoot(String name, RecordingCriteriaBuilder builder) {
        return proxy(Root.class, pathHandler(name, builder));
    }

    private static InvocationHandler pathHandler(String name, RecordingCriteriaBuilder builder) {
        return (proxy, method, args) -> switch (method.getName()) {
            case "get" -> {
                String child = name + "." + args[0];
                if (builder != null) {
                    builder.pathReads.add(child);
                }
                yield proxy(Path.class, pathHandler(child, builder));
            }
            case "as" -> expression(name);
            case "toString" -> name;
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Expression<?> expression(String name) {
        return proxy(Expression.class, (proxy, method, args) -> switch (method.getName()) {
            case "as" -> expression(name);
            case "toString" -> name;
            default -> defaultValue(method.getReturnType());
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

    private static final class RecordingCriteriaQuery<T> {
        private final Class<?> resultType;
        private final List<Order> orders = new ArrayList<>();

        private RecordingCriteriaQuery(Class<?> resultType) {
            this.resultType = resultType;
        }

        private CriteriaQuery<T> proxy() {
            return SearchSpecificationSortingTest.proxy(CriteriaQuery.class, (proxy, method, args) -> {
                if ("getResultType".equals(method.getName())) {
                    return resultType;
                }
                if ("orderBy".equals(method.getName())) {
                    orders.clear();
                    if (args[0] instanceof List<?> list) {
                        list.forEach(order -> orders.add((Order) order));
                    }
                    return proxy;
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static final class RecordingCriteriaBuilder {
        private final List<String> pathReads = new ArrayList<>();
        private final List<Class<?>> treatedTypes = new ArrayList<>();
        private final List<String> orderCalls = new ArrayList<>();

        private CriteriaBuilder proxy() {
            return SearchSpecificationSortingTest.proxy(CriteriaBuilder.class, (proxy, method, args) -> switch (method.getName()) {
                case "treat" -> {
                    treatedTypes.add((Class<?>) args[1]);
                    yield SearchSpecificationSortingTest.proxy(Root.class, pathHandler("treated", this));
                }
                case "lower" -> expression("lower(" + args[0] + ")");
                case "isNull" -> SearchSpecificationSortingTest.proxy(
                        Predicate.class,
                        (predicate, predicateMethod, predicateArgs) -> defaultValue(predicateMethod.getReturnType()));
                case "selectCase" -> selectCase();
                case "asc", "desc" -> {
                    orderCalls.add(method.getName());
                    yield SearchSpecificationSortingTest.proxy(
                            Order.class,
                            (order, orderMethod, orderArgs) -> defaultValue(orderMethod.getReturnType()));
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        private CriteriaBuilder.Case<Integer> selectCase() {
            return SearchSpecificationSortingTest.proxy(
                    CriteriaBuilder.Case.class,
                    (proxy, method, args) -> switch (method.getName()) {
                        case "when" -> proxy;
                        case "otherwise" -> expression("case");
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }
}
