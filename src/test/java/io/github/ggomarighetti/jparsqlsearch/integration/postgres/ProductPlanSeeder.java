package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ProductPlanSeeder {
    public static final int CATEGORY_COUNT = 50;
    public static final int SUPPLIER_COUNT = 200;
    public static final int PRODUCT_COUNT = 20_000;

    private static final int BATCH_SIZE = 1_000;

    private ProductPlanSeeder() {
    }

    public static void seed(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "truncate table product_reviews, catalog_products, product_categories, suppliers restart identity cascade");
        seedCategories(jdbcTemplate);
        seedSuppliers(jdbcTemplate);
        seedProducts(jdbcTemplate);
        seedReviews(jdbcTemplate);
    }

    public static String sku(long id) {
        return id == 2 ? "SKU-QUARTZNEEDLE-00002" : "SKU-%05d".formatted(id);
    }

    public static UUID productUuid(long id) {
        return UUID.nameUUIDFromBytes(("plan-product:" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static BigDecimal price(long id) {
        return BigDecimal.valueOf(10_000L + ((id * 137L) % 250_000L), 2);
    }

    public static LocalDate releaseDate(long id) {
        return LocalDate.of(2020, Month.JANUARY, 1).plusDays(id % 1_800);
    }

    private static void seedCategories(JdbcTemplate jdbcTemplate) {
        List<CategoryRow> rows = new ArrayList<>(CATEGORY_COUNT);
        for (long id = 1; id <= CATEGORY_COUNT; id++) {
            rows.add(new CategoryRow(id, "CAT-%03d".formatted(id), "Category %03d".formatted(id)));
        }
        jdbcTemplate.batchUpdate(
                "insert into product_categories(id, code, name) values (?, ?, ?)",
                rows,
                BATCH_SIZE,
                (statement, row) -> {
                    statement.setLong(1, row.id());
                    statement.setString(2, row.code());
                    statement.setString(3, row.name());
                });
    }

    private static void seedSuppliers(JdbcTemplate jdbcTemplate) {
        List<SupplierRow> rows = new ArrayList<>(SUPPLIER_COUNT);
        for (long id = 1; id <= SUPPLIER_COUNT; id++) {
            rows.add(new SupplierRow(
                    id,
                    UUID.nameUUIDFromBytes(("plan-supplier:" + id).getBytes(StandardCharsets.UTF_8)),
                    "Supplier %03d".formatted(id),
                    id % 5 == 0,
                    "City %03d".formatted(id),
                    countryCode(id)));
        }
        jdbcTemplate.batchUpdate(
                "insert into suppliers(id, public_id, name, preferred, city, country_code) "
                        + "values (?, ?, ?, ?, ?, ?)",
                rows,
                BATCH_SIZE,
                (statement, row) -> {
                    statement.setLong(1, row.id());
                    statement.setObject(2, row.publicId());
                    statement.setString(3, row.name());
                    statement.setBoolean(4, row.preferred());
                    statement.setString(5, row.city());
                    statement.setString(6, row.countryCode());
                });
    }

    private static void seedProducts(JdbcTemplate jdbcTemplate) {
        List<ProductRow> rows = new ArrayList<>(PRODUCT_COUNT);
        for (long id = 1; id <= PRODUCT_COUNT; id++) {
            rows.add(product(id));
        }
        jdbcTemplate.batchUpdate(
                """
                insert into catalog_products(
                    id, public_id, sku, name, description, stock, price, active, archived,
                    release_date, status, category_id, supplier_id, created_by, created_at,
                    weight_kg, width_cm, height_cm
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows,
                BATCH_SIZE,
                ProductPlanSeeder::bindProduct);
    }

    private static ProductRow product(long id) {
        String name = id == 1 ? "Quartzneedle Workstation" : "Plan Product %05d".formatted(id);
        String description = id == 3
                ? "Catalog description containing quartzneedle for trigram search"
                : "Deterministic product %05d for PostgreSQL plan verification".formatted(id);
        return new ProductRow(
                id,
                productUuid(id),
                sku(id),
                name,
                description,
                (int) (id % 100),
                price(id),
                id <= 3 || id % 10 != 0,
                false,
                releaseDate(id),
                status(id),
                categoryId(id),
                supplierId(id),
                "plan-seeder-%02d".formatted(id % 20),
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds((id % 31_536_000L)),
                BigDecimal.valueOf(100 + (id % 400), 2),
                BigDecimal.valueOf(2_000 + (id % 2_000), 2),
                BigDecimal.valueOf(1_000 + (id % 1_000), 2));
    }

    private static void bindProduct(PreparedStatement statement, ProductRow row) throws java.sql.SQLException {
        statement.setLong(1, row.id());
        statement.setObject(2, row.publicId());
        statement.setString(3, row.sku());
        statement.setString(4, row.name());
        statement.setString(5, row.description());
        statement.setInt(6, row.stock());
        statement.setBigDecimal(7, row.price());
        statement.setBoolean(8, row.active());
        statement.setBoolean(9, row.archived());
        statement.setObject(10, row.releaseDate());
        statement.setString(11, row.status());
        statement.setLong(12, row.categoryId());
        statement.setLong(13, row.supplierId());
        statement.setString(14, row.createdBy());
        statement.setTimestamp(15, Timestamp.from(row.createdAt()));
        statement.setBigDecimal(16, row.weightKg());
        statement.setBigDecimal(17, row.widthCm());
        statement.setBigDecimal(18, row.heightCm());
    }

    private static void seedReviews(JdbcTemplate jdbcTemplate) {
        List<ReviewRow> rows = new ArrayList<>(PRODUCT_COUNT * 4);
        long reviewId = 1;
        for (long productId = 1; productId <= PRODUCT_COUNT; productId++) {
            int reviewCount = (int) (productId % 9);
            for (int index = 0; index < reviewCount; index++) {
                rows.add(new ReviewRow(
                        reviewId++,
                        1 + (int) ((productId + index) % 5),
                        "Review %d for product %d".formatted(index, productId),
                        index % 4 != 0,
                        Instant.parse("2024-02-01T00:00:00Z").plusSeconds(productId + index),
                        productId));
            }
        }
        jdbcTemplate.batchUpdate(
                "insert into product_reviews(id, rating, title, verified, created_at, product_id) "
                        + "values (?, ?, ?, ?, ?, ?)",
                rows,
                BATCH_SIZE,
                (statement, row) -> {
                    statement.setLong(1, row.id());
                    statement.setInt(2, row.rating());
                    statement.setString(3, row.title());
                    statement.setBoolean(4, row.verified());
                    statement.setTimestamp(5, Timestamp.from(row.createdAt()));
                    statement.setLong(6, row.productId());
                });
    }

    private static long categoryId(long productId) {
        return productId % 100 < 60 ? 1 : 2 + productId % 49;
    }

    private static long supplierId(long productId) {
        return productId % 100 < 50 ? 1 : 2 + productId % 199;
    }

    private static String countryCode(long supplierId) {
        return switch ((int) (supplierId % 10)) {
            case 0, 1, 2, 3, 4, 5 -> "US";
            case 6, 7 -> "DE";
            case 8 -> "AR";
            default -> "BR";
        };
    }

    private static String status(long id) {
        if (id <= 3 || id % 20 < 17) {
            return "PUBLISHED";
        }
        return id % 20 < 19 ? "DRAFT" : "DISCONTINUED";
    }

    private record CategoryRow(long id, String code, String name) {
    }

    private record SupplierRow(
            long id,
            UUID publicId,
            String name,
            boolean preferred,
            String city,
            String countryCode) {
    }

    private record ProductRow(
            long id,
            UUID publicId,
            String sku,
            String name,
            String description,
            int stock,
            BigDecimal price,
            boolean active,
            boolean archived,
            LocalDate releaseDate,
            String status,
            long categoryId,
            long supplierId,
            String createdBy,
            Instant createdAt,
            BigDecimal weightKg,
            BigDecimal widthCm,
            BigDecimal heightCm) {
    }

    private record ReviewRow(
            long id,
            int rating,
            String title,
            boolean verified,
            Instant createdAt,
            long productId) {
    }
}
