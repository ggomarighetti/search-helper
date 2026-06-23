package io.github.ggomarighetti.jparsqlsearch.property;

import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LIKE;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.page.validation.SearchPageableValidationException;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Status;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.data.domain.Sort;

public final class SearchPropertyFixtures {
    public static final Set<String> RSQL_CODES = Set.of(
            RsqlFilterValidationException.PARSE_ERROR,
            RsqlFilterValidationException.RULES_FORBIDDEN,
            RsqlFilterValidationException.LIMIT_EXCEEDED);

    public static final Set<String> PAGEABLE_CODES = Set.of(
            SearchPageableValidationException.PAGE_LIMIT_EXCEEDED,
            SearchPageableValidationException.PAGE_RULES_FORBIDDEN,
            SearchPageableValidationException.SORT_LIMIT_EXCEEDED,
            SearchPageableValidationException.SORT_RULES_FORBIDDEN);

    public static final Map<String, String> SORTING_PATHS = Map.of(
            "sku", "sku",
            "name", "name",
            "amount", "price",
            "legacyAmount", "price",
            "releaseDate", "releaseDate",
            "supplierName", "supplier.name");

    private SearchPropertyFixtures() {
    }

    public static SearchDefinition<Product> rsqlDefinition() {
        return rsqlDefinition(null);
    }

    public static SearchDefinition<Product> rsqlDefinition(Consumer<SearchPolicy.Builder> limits) {
        SearchDefinition.Builder<Product> builder = SearchDefinition.builder().entity(Product.class);
        if (limits != null) {
            builder.limits(limits);
        }
        return builder
                .fields(fields -> {
                    fields.add("sku", String.class)
                            .filterable(filter -> filter.allow(EQUAL, IN, LIKE));
                    fields.add("name", String.class)
                            .filterable(filter -> filter.allow(EQUAL, IN, LIKE));
                    fields.add("amount", BigDecimal.class)
                            .path("price")
                            .filterable(filter -> filter.allow(
                                    EQUAL,
                                    IN,
                                    GREATER_THAN,
                                    GREATER_THAN_OR_EQUAL,
                                    LESS_THAN,
                                    LESS_THAN_OR_EQUAL));
                    fields.add("stock", Integer.class)
                            .filterable(filter -> filter.allow(
                                    EQUAL,
                                    IN,
                                    GREATER_THAN,
                                    GREATER_THAN_OR_EQUAL,
                                    LESS_THAN,
                                    LESS_THAN_OR_EQUAL));
                    fields.add("status", Status.class)
                            .filterable(filter -> filter.allow(EQUAL, IN));
                    fields.add("releaseDate", LocalDate.class)
                            .filterable(filter -> filter.allow(
                                    EQUAL,
                                    GREATER_THAN,
                                    GREATER_THAN_OR_EQUAL,
                                    LESS_THAN,
                                    LESS_THAN_OR_EQUAL));
                    fields.add("reviewRating", Integer.class)
                            .path("reviews.rating")
                            .filterable(filter -> filter.allow(EQUAL, IN));
                })
                .build();
    }

    public static SearchDefinition<Product> pageableDefinition() {
        return pageableDefinition(null);
    }

    public static SearchDefinition<Product> pageableDefinition(Consumer<SearchPolicy.Builder> limits) {
        SearchDefinition.Builder<Product> builder = SearchDefinition.builder().entity(Product.class);
        if (limits != null) {
            builder.limits(limits);
        }
        return builder
                .fields(fields -> {
                    fields.add("sku", String.class)
                            .sortable(sort -> sort.allowIgnoreCase()
                                    .allowNullHandling(Sort.NullHandling.NULLS_LAST));
                    fields.add("name", String.class)
                            .sortable(sort -> sort.allowIgnoreCase()
                                    .allowNullHandling(Sort.NullHandling.NULLS_LAST));
                    fields.add("amount", BigDecimal.class)
                            .path("price")
                            .sortable();
                    fields.add("legacyAmount", BigDecimal.class)
                            .path("price")
                            .sortable();
                    fields.add("releaseDate", LocalDate.class)
                            .sortable(sort -> sort.allow(Sort.Direction.DESC));
                    fields.add("supplierName", String.class)
                            .path("supplier.name")
                            .sortable(SearchSorting.Builder::allowIgnoreCase);
                    fields.add("status", Status.class);
                })
                .paging()
                .build();
    }

    public static boolean isExpectedRsqlThrowable(Throwable throwable) {
        if (throwable instanceof RsqlFilterValidationException exception) {
            return RSQL_CODES.contains(exception.code());
        }
        return throwable instanceof SearchProtectionException;
    }

    public static boolean isExpectedPageableThrowable(Throwable throwable) {
        if (throwable instanceof SearchPageableValidationException exception) {
            return PAGEABLE_CODES.contains(exception.code());
        }
        return throwable instanceof SearchProtectionException;
    }
}
