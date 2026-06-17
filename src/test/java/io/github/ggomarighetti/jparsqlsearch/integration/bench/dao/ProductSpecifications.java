package io.github.ggomarighetti.jparsqlsearch.integration.bench.dao;

import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Status;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {
    private ProductSpecifications() {
    }

    public static Specification<Product> matchesTerm(String term) {
        String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("name")), pattern),
                builder.like(builder.lower(root.get("sku")), pattern),
                builder.like(builder.lower(root.get("description")), pattern));
    }

    public static Specification<Product> publishedProducts() {
        return (root, query, builder) -> builder.equal(root.get("status"), Status.PUBLISHED);
    }
}
