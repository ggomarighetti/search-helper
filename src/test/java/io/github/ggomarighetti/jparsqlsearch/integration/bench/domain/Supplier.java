package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean preferred;

    @Embedded
    private Address address;

    protected Supplier() {
    }

    Supplier(Long id, UUID publicId, String name, Boolean preferred, Address address) {
        this.id = id;
        this.publicId = publicId;
        this.name = name;
        this.preferred = preferred;
        this.address = address;
    }

    public static SupplierBuilder builder() {
        return new SupplierBuilder();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public Boolean getPreferred() {
        return preferred;
    }

    public Address getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Supplier supplier)) {
            return false;
        }
        return Objects.equals(publicId, supplier.publicId)
                && Objects.equals(name, supplier.name)
                && Objects.equals(preferred, supplier.preferred)
                && Objects.equals(address, supplier.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicId, name, preferred, address);
    }

    @Override
    public String toString() {
        return "Supplier{id=%s, publicId=%s, name='%s', preferred=%s, address=%s}"
                .formatted(id, publicId, name, preferred, address);
    }

    public static final class SupplierBuilder {
        private Long id;
        private UUID publicId;
        private String name;
        private Boolean preferred;
        private Address address;

        private SupplierBuilder() {
        }

        public SupplierBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public SupplierBuilder publicId(UUID publicId) {
            this.publicId = publicId;
            return this;
        }

        public SupplierBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SupplierBuilder preferred(Boolean preferred) {
            this.preferred = preferred;
            return this;
        }

        public SupplierBuilder address(Address address) {
            this.address = address;
            return this;
        }

        public Supplier build() {
            return new Supplier(id, publicId, name, preferred, address);
        }
    }
}
