package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "product_reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Boolean verified;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    protected Review() {
    }

    Review(Integer rating, String title, Boolean verified, Instant createdAt, Product product) {
        this.rating = rating;
        this.title = title;
        this.verified = verified;
        this.createdAt = createdAt;
        this.product = product;
    }

    public Long getId() {
        return id;
    }

    public Integer getRating() {
        return rating;
    }

    public String getTitle() {
        return title;
    }

    public Boolean getVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Product getProduct() {
        return product;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Review review)) {
            return false;
        }
        return Objects.equals(rating, review.rating)
                && Objects.equals(title, review.title)
                && Objects.equals(verified, review.verified)
                && Objects.equals(createdAt, review.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rating, title, verified, createdAt);
    }

    @Override
    public String toString() {
        return "Review{id=%s, rating=%s, title='%s', verified=%s, createdAt=%s}"
                .formatted(id, rating, title, verified, createdAt);
    }
}
