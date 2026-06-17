package io.github.ggomarighetti.jparsqlsearch.compile;

import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Validated search artifacts ready to pass to a Spring Data JPA repository.
 *
 * @param <T> entity type targeted by the specification
 * @param specification combined and validated JPA specification
 * @param pageable validated pageable, with public sort aliases translated
 */
public record CompiledSearch<T>(Specification<T> specification, Pageable pageable) {
    /**
     * Creates a compiled search and rejects missing artifacts.
     */
    public CompiledSearch {
        Objects.requireNonNull(specification, "specification must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
    }
}
