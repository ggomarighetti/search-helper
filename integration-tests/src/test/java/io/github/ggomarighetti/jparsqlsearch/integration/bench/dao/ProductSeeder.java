package io.github.ggomarighetti.jparsqlsearch.integration.bench.dao;

import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Address;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Audit;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Category;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Dimensions;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Status;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Supplier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.function.Consumer;
import java.util.UUID;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

public final class ProductSeeder {
    private ProductSeeder() {
    }

    public static Catalog seedCatalog(TestEntityManager entityManager) {
        Category laptops = persist(entityManager, category("LAPTOP", "Laptops"));
        Category phones = persist(entityManager, category("PHONE", "Phones"));
        Category accessories = persist(entityManager, category("ACCESSORY", "Accessories"));

        Supplier acme = persist(entityManager, supplier("Acme Supply", true, "Austin", "US"));
        Supplier globex = persist(entityManager, supplier("Globex Imports", false, "Berlin", "DE"));

        Product gamingLaptop = persist(entityManager, product(
                "LAP-GAME-001",
                "Gaming Laptop",
                laptops,
                acme,
                product -> product
                        .price(amount("1899.99"))
                        .stock(32)
                        .audit(audit("gaming-team", "2024-02-10T10:15:30Z"))
                        .dimensions(dimensions("2.40", "35.00", "24.00"))));
        review(gamingLaptop, 5, "Excellent performance", true, "2024-03-01T12:00:00Z");
        review(gamingLaptop, 4, "Solid battery", true, "2024-03-02T12:00:00Z");
        review(gamingLaptop, 4, "Unverified note", false, "2024-03-03T12:00:00Z");

        Product workstationLaptop = persist(entityManager, product(
                "LAP-WORK-001",
                "Workstation Laptop",
                laptops,
                acme,
                product -> product
                        .price(amount("2499.00"))
                        .stock(18)
                        .audit(audit("workstation-team", "2024-02-11T10:15:30Z"))
                        .dimensions(dimensions("2.80", "36.00", "25.00"))));
        review(workstationLaptop, 5, "Great for CAD", true, "2024-03-04T12:00:00Z");
        review(workstationLaptop, 5, "Fast compile times", true, "2024-03-05T12:00:00Z");

        Product officeLaptop = persist(entityManager, product(
                "LAP-OFFICE-001",
                "Office Laptop",
                laptops,
                globex,
                product -> product
                        .price(amount("799.00"))
                        .stock(8)));
        review(officeLaptop, 3, "Good enough", true, "2024-03-06T12:00:00Z");

        Product phone = persist(entityManager, product(
                "PHONE-001",
                "Compact Phone",
                phones,
                acme,
                product -> product
                        .price(amount("699.00"))
                        .stock(44)));
        review(phone, 4, "Bright display", true, "2024-03-07T12:00:00Z");

        persist(entityManager, product(
                "LAP-DRAFT-001",
                "Draft Laptop",
                laptops,
                acme,
                product -> product
                        .status(Status.DRAFT)
                        .price(amount("2199.00"))));

        persist(entityManager, product(
                "LAP-DISC-001",
                "Discontinued Laptop",
                laptops,
                acme,
                product -> product
                        .status(Status.DISCONTINUED)
                        .price(amount("2299.00"))));

        persist(entityManager, product(
                "ACC-KEY-001",
                "Mechanical Keyboard",
                accessories,
                globex,
                product -> product
                        .price(amount("149.00"))
                        .stock(65)));

        entityManager.flush();
        entityManager.clear();

        return new Catalog(
                gamingLaptop, workstationLaptop, officeLaptop, phone,
                laptops, acme);
    }

    private static Product product(
            String sku,
            String name,
            Category category,
            Supplier supplier,
            Consumer<Product.ProductBuilder> overrides) {
        Product.ProductBuilder product = Product.builder()
                .publicId(stableUuid("product:" + sku))
                .sku(sku)
                .name(name)
                .description(name + " for the catalog search bench")
                .stock(20)
                .price(amount("499.00"))
                .active(true)
                .archived(false)
                .releaseDate(LocalDate.of(2024, Month.JANUARY, 15))
                .status(Status.PUBLISHED)
                .category(category)
                .supplier(supplier)
                .audit(audit("catalog-seed", "2024-01-01T09:00:00Z"))
                .dimensions(dimensions("1.25", "30.00", "20.00"));
        overrides.accept(product);
        return product.build();
    }

    private static Category category(String code, String name) {
        return Category.builder()
                .code(code)
                .name(name)
                .build();
    }

    private static Supplier supplier(
            String name,
            boolean preferred,
            String city,
            String countryCode) {
        return Supplier.builder()
                .publicId(stableUuid("supplier:" + name))
                .name(name)
                .preferred(preferred)
                .address(address(city, countryCode))
                .build();
    }

    private static Address address(String city, String countryCode) {
        return Address.builder()
                .city(city)
                .countryCode(countryCode)
                .build();
    }

    private static Audit audit(String createdBy, String createdAt) {
        return Audit.builder()
                .createdBy(createdBy)
                .createdAt(Instant.parse(createdAt))
                .build();
    }

    private static Dimensions dimensions(String weightKg, String widthCm, String heightCm) {
        return Dimensions.builder()
                .weightKg(amount(weightKg))
                .widthCm(amount(widthCm))
                .heightCm(amount(heightCm))
                .build();
    }

    private static void review(
            Product product,
            int rating,
            String title,
            boolean verified,
            String createdAt) {
        product.addReview(rating, title, verified, Instant.parse(createdAt));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> T persist(TestEntityManager entityManager, T entity) {
        return entityManager.persist(entity);
    }

    public record Catalog(
            Product gamingLaptop, Product workstationLaptop, Product officeLaptop, Product phone,
            Category laptops, Supplier acme
    ) {}
}
