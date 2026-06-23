package io.github.ggomarighetti.jparsqlsearch.integration.bench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "product_categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    protected Category() {
    }

    Category(Long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public static CategoryBuilder builder() {
        return new CategoryBuilder();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category category)) {
            return false;
        }
        return Objects.equals(code, category.code)
                && Objects.equals(name, category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name);
    }

    @Override
    public String toString() {
        return "Category{id=%s, code='%s', name='%s'}".formatted(id, code, name);
    }

    public static final class CategoryBuilder {
        private Long id;
        private String code;
        private String name;

        private CategoryBuilder() {
        }

        public CategoryBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CategoryBuilder code(String code) {
            this.code = code;
            return this;
        }

        public CategoryBuilder name(String name) {
            this.name = name;
            return this;
        }

        public Category build() {
            return new Category(id, code, name);
        }
    }
}
