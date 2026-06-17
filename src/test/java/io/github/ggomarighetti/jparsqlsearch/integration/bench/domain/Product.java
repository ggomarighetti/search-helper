package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "catalog_products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Boolean archived;

    @Column(nullable = false)
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Embedded
    private Audit audit;

    @Embedded
    private Dimensions dimensions;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    protected Product() {
    }

    public void addReview(Integer rating, String title, Boolean verified, Instant createdAt) {
        Review review = new Review(rating, title, verified, createdAt, this);
        reviews.add(review);
    }

    @Transient
    public String getDisplayName() {
        return getName();
    }

    Product(
            Long id,
            UUID publicId,
            String sku,
            String name,
            String description,
            Integer stock,
            BigDecimal price,
            Boolean active,
            Boolean archived,
            LocalDate releaseDate,
            Status status,
            Category category,
            Supplier supplier,
            Audit audit,
            Dimensions dimensions,
            List<Review> reviews) {
        this.id = id;
        this.publicId = publicId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.stock = stock;
        this.price = price;
        this.active = active;
        this.archived = archived;
        this.releaseDate = releaseDate;
        this.status = status;
        this.category = category;
        this.supplier = supplier;
        this.audit = audit;
        this.dimensions = dimensions;
        this.reviews = reviews == null ? new ArrayList<>() : reviews;
    }

    public static ProductBuilder builder() {
        return new ProductBuilder();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getStock() {
        return stock;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getArchived() {
        return archived;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public Status getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Audit getAudit() {
        return audit;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Product product)) {
            return false;
        }
        return Objects.equals(publicId, product.publicId)
                && Objects.equals(sku, product.sku)
                && Objects.equals(name, product.name)
                && Objects.equals(description, product.description)
                && Objects.equals(stock, product.stock)
                && Objects.equals(price, product.price)
                && Objects.equals(active, product.active)
                && Objects.equals(archived, product.archived)
                && Objects.equals(releaseDate, product.releaseDate)
                && status == product.status
                && Objects.equals(category, product.category)
                && Objects.equals(supplier, product.supplier)
                && Objects.equals(audit, product.audit)
                && Objects.equals(dimensions, product.dimensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                publicId,
                sku,
                name,
                description,
                stock,
                price,
                active,
                archived,
                releaseDate,
                status,
                category,
                supplier,
                audit,
                dimensions);
    }

    @Override
    public String toString() {
        return "Product{id=%s, publicId=%s, sku='%s', name='%s', stock=%s, price=%s, active=%s, archived=%s, releaseDate=%s, status=%s}"
                .formatted(id, publicId, sku, name, stock, price, active, archived, releaseDate, status);
    }

    public static final class ProductBuilder {
        private Long id;
        private UUID publicId;
        private String sku;
        private String name;
        private String description;
        private Integer stock;
        private BigDecimal price;
        private Boolean active;
        private Boolean archived;
        private LocalDate releaseDate;
        private Status status;
        private Category category;
        private Supplier supplier;
        private Audit audit;
        private Dimensions dimensions;
        private List<Review> reviews;

        private ProductBuilder() {
        }

        public ProductBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProductBuilder publicId(UUID publicId) {
            this.publicId = publicId;
            return this;
        }

        public ProductBuilder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public ProductBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ProductBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ProductBuilder stock(Integer stock) {
            this.stock = stock;
            return this;
        }

        public ProductBuilder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public ProductBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public ProductBuilder archived(Boolean archived) {
            this.archived = archived;
            return this;
        }

        public ProductBuilder releaseDate(LocalDate releaseDate) {
            this.releaseDate = releaseDate;
            return this;
        }

        public ProductBuilder status(Status status) {
            this.status = status;
            return this;
        }

        public ProductBuilder category(Category category) {
            this.category = category;
            return this;
        }

        public ProductBuilder supplier(Supplier supplier) {
            this.supplier = supplier;
            return this;
        }

        public ProductBuilder audit(Audit audit) {
            this.audit = audit;
            return this;
        }

        public ProductBuilder dimensions(Dimensions dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public ProductBuilder reviews(List<Review> reviews) {
            this.reviews = reviews;
            return this;
        }

        public Product build() {
            return new Product(
                    id,
                    publicId,
                    sku,
                    name,
                    description,
                    stock,
                    price,
                    active,
                    archived,
                    releaseDate,
                    status,
                    category,
                    supplier,
                    audit,
                    dimensions,
                    reviews);
        }
    }
}
