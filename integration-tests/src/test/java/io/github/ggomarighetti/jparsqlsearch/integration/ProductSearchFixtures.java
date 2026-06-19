package io.github.ggomarighetti.jparsqlsearch.integration;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductSpecifications;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.validator.cfg.defs.MaxDef;
import org.hibernate.validator.cfg.defs.MinDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

final class ProductSearchFixtures {
    private ProductSearchFixtures() {
    }

    static SearchDefinition<Product> standardProductSearch() {
        return standardProductSearch(8);
    }

    static SearchDefinition<Product> standardProductSearch(int maxComparisonsPerSelector) {
        return SearchDefinition.builder().entity(Product.class)
                .limits(limits -> {
                    limits.rsql(rsql -> rsql.maxLength(2048));
                    limits.filter(filter -> filter.maxComparisonsPerSelector(maxComparisonsPerSelector));
                    limits.paths(paths -> paths.maxDepth(4));
                    limits.paging(paging -> paging
                            .maxPage(20)
                            .maxSize(50));
                    limits.sorting(sorting -> sorting.maxOrders(3));
                    limits.query(query -> query.maxLength(80));
                })
                .fields(fields -> {
                    fields.add("internalId", Long.class)
                            .path("id")
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("publicId", UUID.class)
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("sku", String.class)
                            .filterable(filter -> filter.allow(EQUAL, IN))
                            .sortable(sort -> sort.allow(ASC));
                    fields.add("name", String.class)
                            .filterable(filter -> filter.allow(EQUAL))
                            .sortable(sort -> sort.allow(ASC));
                    fields.add("categoryCode", String.class)
                            .filterable(filter -> filter.path("category.code").allow(EQUAL, IN))
                            .sortable(sort -> sort.path("category.code").allow(ASC));
                    fields.add("supplierName", String.class)
                            .filterable(filter -> filter.path("supplier.name").allow(EQUAL))
                            .sortable(sort -> sort.path("supplier.name").allow(ASC));
                    fields.add("supplierPreferred", Boolean.class)
                            .filterable(filter -> filter.path("supplier.preferred").allow(EQUAL));
                    fields.add("supplierCountry", String.class)
                            .filterable(filter -> filter.path("supplier.address.countryCode").allow(EQUAL));
                    fields.add("active", Boolean.class)
                            .filterable(filter -> filter.allow(EQUAL));
                    fields.add("price", BigDecimal.class)
                            .filterable(filter -> filter.allow(EQUAL, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL))
                            .sortable(sort -> sort.allow(DESC, ASC));
                    fields.add("stock", Integer.class)
                            .filterable(filter -> filter.allow(EQUAL, GREATER_THAN_OR_EQUAL));
                    fields.add("releasedOn", LocalDate.class)
                            .path("releaseDate")
                            .filterable(filter -> filter.allow(EQUAL, GREATER_THAN_OR_EQUAL, LESS_THAN))
                            .sortable(sort -> sort.path("releaseDate").allow(DESC, ASC));
                    fields.add("createdAt", Instant.class)
                            .filterable(filter -> filter.path("audit.createdAt").allow(EQUAL, GREATER_THAN_OR_EQUAL));
                    fields.add("createdBy", String.class)
                            .filterable(filter -> filter.path("audit.createdBy").allow(EQUAL));
                    fields.add("weightKg", BigDecimal.class)
                            .filterable(filter -> filter.path("dimensions.weightKg").allow(
                                    EQUAL,
                                    GREATER_THAN_OR_EQUAL,
                                    LESS_THAN_OR_EQUAL));
                    fields.add("reviewRating", Integer.class)
                            .filterable(filter -> filter.path("reviews.rating").allow(GREATER_THAN_OR_EQUAL));
                })
                .query(query -> query
                        .rule(new SizeDef().min(3).max(80))
                        .specification(ProductSpecifications::matchesTerm))
                .paging(paging -> {
                    paging.page(page -> {
                        page.rule(new MinDef().value(0));
                        page.rule(new MaxDef().value(20));
                    });
                    paging.size(size -> {
                        size.rule(new MinDef().value(1));
                        size.rule(new MaxDef().value(50));
                    });
                })
                .build();
    }
}
