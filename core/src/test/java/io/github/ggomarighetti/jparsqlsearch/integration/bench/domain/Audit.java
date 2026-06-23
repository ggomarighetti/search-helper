package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class Audit {
    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected Audit() {
    }

    Audit(String createdBy, Instant createdAt) {
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static AuditBuilder builder() {
        return new AuditBuilder();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Audit audit)) {
            return false;
        }
        return Objects.equals(createdBy, audit.createdBy)
                && Objects.equals(createdAt, audit.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(createdBy, createdAt);
    }

    @Override
    public String toString() {
        return "Audit{createdBy='%s', createdAt=%s}".formatted(createdBy, createdAt);
    }

    public static final class AuditBuilder {
        private String createdBy;
        private Instant createdAt;

        private AuditBuilder() {
        }

        public AuditBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public AuditBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Audit build() {
            return new Audit(createdBy, createdAt);
        }
    }
}
