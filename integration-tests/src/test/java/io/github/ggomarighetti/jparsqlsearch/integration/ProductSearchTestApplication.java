package io.github.ggomarighetti.jparsqlsearch.integration;

import io.github.ggomarighetti.jparsqlsearch.integration.bench.dao.ProductRepository;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackageClasses = ProductRepository.class)
@EntityScan(basePackageClasses = Product.class)
@Import(JpaRsqlSearchTestAutoConfigurationImportSelector.class)
class ProductSearchTestApplication {
}
