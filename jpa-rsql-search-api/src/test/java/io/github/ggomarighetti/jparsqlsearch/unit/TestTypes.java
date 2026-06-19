package io.github.ggomarighetti.jparsqlsearch.unit;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TestTypes {
    private TestTypes() {
    }

    @Entity
    public static class Product {
        public BigDecimal getPrice() {
            return null;
        }

        public String getEmail() {
            return null;
        }

        public String getName() {
            return null;
        }

        public Instant getCreatedAt() {
            return null;
        }

        public Person getPerson() {
            return null;
        }

        public Owner getOwner() {
            return null;
        }

        public Customer getCustomer() {
            return null;
        }

        public List<String> getTags() {
            return null;
        }

        @ManyToOne
        public Category getCategory() {
            return null;
        }

        @OneToMany
        public List<Review> getReviews() {
            return null;
        }

        @SuppressWarnings("rawtypes")
        public List getRawReviews() {
            return null;
        }

        public Status getStatus() {
            return null;
        }
    }

    @Entity
    public static final class Category {
        public String getName() {
            return null;
        }
    }

    public static final class PerishableProduct extends Product {
        public Instant getExpiresAt() {
            return null;
        }
    }

    public static final class Review {
        public int getRating() {
            return 0;
        }
    }

    public static final class Person {
        public String getTaxIdentifier() {
            return null;
        }
    }

    public static final class Owner {
        public String getTaxIdentifier() {
            return null;
        }
    }

    public static final class Customer {
        public String getName() {
            return null;
        }

        public String getSortName() {
            return null;
        }

        public Region getRegion() {
            return null;
        }
    }

    public static final class Region {
        public Country getCountry() {
            return null;
        }
    }

    public static final class Country {
        public String getCode() {
            return null;
        }
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }
}
