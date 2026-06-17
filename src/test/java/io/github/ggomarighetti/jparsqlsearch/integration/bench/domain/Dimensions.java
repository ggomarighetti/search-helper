package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public class Dimensions {
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal heightCm;

    protected Dimensions() {
    }

    Dimensions(BigDecimal weightKg, BigDecimal widthCm, BigDecimal heightCm) {
        this.weightKg = weightKg;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public static DimensionsBuilder builder() {
        return new DimensionsBuilder();
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Dimensions dimensions)) {
            return false;
        }
        return Objects.equals(weightKg, dimensions.weightKg)
                && Objects.equals(widthCm, dimensions.widthCm)
                && Objects.equals(heightCm, dimensions.heightCm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weightKg, widthCm, heightCm);
    }

    @Override
    public String toString() {
        return "Dimensions{weightKg=%s, widthCm=%s, heightCm=%s}"
                .formatted(weightKg, widthCm, heightCm);
    }

    public static final class DimensionsBuilder {
        private BigDecimal weightKg;
        private BigDecimal widthCm;
        private BigDecimal heightCm;

        private DimensionsBuilder() {
        }

        public DimensionsBuilder weightKg(BigDecimal weightKg) {
            this.weightKg = weightKg;
            return this;
        }

        public DimensionsBuilder widthCm(BigDecimal widthCm) {
            this.widthCm = widthCm;
            return this;
        }

        public DimensionsBuilder heightCm(BigDecimal heightCm) {
            this.heightCm = heightCm;
            return this;
        }

        public Dimensions build() {
            return new Dimensions(weightKg, widthCm, heightCm);
        }
    }
}
