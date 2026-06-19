package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

final class SearchSpecificationSorting {
    private SearchSpecificationSorting() {
    }

    static boolean requiresCriteriaSorting(
            Pageable source,
            SearchDefinition<?> definition) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        return source.getSort().stream()
                .map(order -> definition.field(order.getProperty()).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(field -> field.subtype().isPresent());
    }

    static Pageable withoutSort(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.unsorted());
    }

    static <T> Specification<T> apply(
            Specification<T> specification,
            Sort sourceSort,
            SearchDefinition<T> definition) {
        Objects.requireNonNull(specification, "specification must not be null");
        Objects.requireNonNull(sourceSort, "sourceSort must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        List<Sort.Order> requestedOrders = sourceSort.toList();
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            if (!isCountQuery(query.getResultType())) {
                query.orderBy(requestedOrders.stream()
                        .flatMap(order -> criteriaOrders(order, definition, root, criteriaBuilder).stream())
                        .toList());
            }
            return predicate;
        };
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    private static List<Order> criteriaOrders(
            Sort.Order order,
            SearchDefinition<?> definition,
            Root<?> root,
            CriteriaBuilder criteriaBuilder) {
        SearchField<?> field = definition.field(order.getProperty()).orElseThrow();
        From<?, ?> pathRoot = field.subtype()
                .<From<?, ?>>map(subtype -> treat(criteriaBuilder, root, subtype))
                .orElse(root);
        Expression<?> expression = path(pathRoot, field.sorting().path());
        if (order.isIgnoreCase()) {
            expression = criteriaBuilder.lower(expression.as(String.class));
        }

        List<Order> orders = new ArrayList<>(2);
        if (order.getNullHandling() != Sort.NullHandling.NATIVE) {
            int nullRank = order.getNullHandling() == Sort.NullHandling.NULLS_FIRST ? 0 : 1;
            int valueRank = 1 - nullRank;
            Expression<Integer> nullOrdering = criteriaBuilder.<Integer>selectCase()
                    .when(criteriaBuilder.isNull(expression), nullRank)
                    .otherwise(valueRank);
            orders.add(criteriaBuilder.asc(nullOrdering));
        }
        orders.add(order.isAscending()
                ? criteriaBuilder.asc(expression)
                : criteriaBuilder.desc(expression));
        return orders;
    }

    private static Path<?> path(Path<?> root, String path) {
        Path<?> current = root;
        for (String segment : SearchPath.segments(path)) {
            current = current.get(segment);
        }
        return current;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static From<?, ?> treat(
            CriteriaBuilder criteriaBuilder,
            Root<?> root,
            Class<?> subtype) {
        return criteriaBuilder.treat((Root) root, (Class) subtype);
    }
}
