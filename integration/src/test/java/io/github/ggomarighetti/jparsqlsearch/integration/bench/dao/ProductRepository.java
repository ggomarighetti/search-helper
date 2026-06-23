package io.github.ggomarighetti.jparsqlsearch.integration.bench.dao;

import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends
        JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {
}
