package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresSchemaInitializer {
    private static final List<String> INDEXES = List.of(
            "create index idx_products_category on catalog_products(category_id)",
            "create index idx_products_supplier on catalog_products(supplier_id)",
            "create index idx_reviews_product on product_reviews(product_id)",
            "create index idx_reviews_rating_product on product_reviews(rating, product_id)",
            "create index idx_products_category_search "
                    + "on catalog_products(category_id, status, active, price desc, id)",
            "create index idx_products_supplier_search "
                    + "on catalog_products(supplier_id, status, active, price desc, id)",
            "create index idx_suppliers_country on suppliers(country_code)",
            "create index idx_suppliers_name on suppliers(name)",
            "create index idx_products_price on catalog_products(price)",
            "create index idx_products_stock on catalog_products(stock)",
            "create index idx_products_release_date on catalog_products(release_date)",
            "create index idx_products_created_at on catalog_products(created_at)",
            "create index idx_products_name_trgm on catalog_products using gin (lower(name) gin_trgm_ops)",
            "create index idx_products_sku_trgm on catalog_products using gin (lower(sku) gin_trgm_ops)",
            "create index idx_products_description_trgm "
                    + "on catalog_products using gin (lower(description) gin_trgm_ops)");

    private PostgresSchemaInitializer() {
    }

    public static void initialize(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("create extension if not exists pg_trgm");
    }

    public static void createIndexesAndAnalyze(JdbcTemplate jdbcTemplate) {
        for (String index : INDEXES) {
            jdbcTemplate.execute(index);
        }
        jdbcTemplate.execute("analyze");
    }
}
