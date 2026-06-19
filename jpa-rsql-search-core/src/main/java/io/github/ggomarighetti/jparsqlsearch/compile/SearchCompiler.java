package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.definition.validation.SearchDefinitionValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Facade that validates and compiles one complete search request.
 *
 * <p>The compiler coordinates filtering, free-text query, paging, sorting, and
 * cross-component protection rules under a single request context. Applications
 * should use this type instead of invoking internal validators independently.
 */
public final class SearchCompiler {
    private final RsqlSearchGuard rsqlSearchGuard;
    private final SearchQueryGuard searchQueryGuard;
    private final SearchPageableGuard searchPageableGuard;
    private final SearchPolicy policy;

    /**
     * Creates a compiler with an explicit engine and policy.
     *
     * @param engine configured RSQL engine
     * @param policy global protection policy
     */
    public SearchCompiler(SearchRsqlEngine engine, SearchPolicy policy) {
        this(engine, policy, List.of());
    }

    /**
     * Creates a compiler with runtime definition validators.
     *
     * @param engine configured RSQL engine
     * @param policy global protection policy
     * @param definitionValidators validators executed once per definition instance
     */
    public SearchCompiler(
            SearchRsqlEngine engine,
            SearchPolicy policy,
            Collection<? extends SearchDefinitionValidator> definitionValidators) {
        Objects.requireNonNull(engine, "engine must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.rsqlSearchGuard = new RsqlSearchGuard(engine, policy, definitionValidators);
        this.searchQueryGuard = new SearchQueryGuard(policy);
        this.searchPageableGuard = new SearchPageableGuard(policy);
    }

    /**
     * Compiles a request intended for a paged query with a count.
     *
     * @param filter optional RSQL filter
     * @param query optional free-text query
     * @param pageable requested page and sort
     * @param definition allowed search capabilities
     * @param specifications mandatory application specifications combined with {@code AND}
     * @param <T> entity type
     * @return validated specification and pageable
     */
    @SafeVarargs
    public final <T> CompiledSearch<T> compile(
            String filter,
            String query,
            Pageable pageable,
            SearchDefinition<T> definition,
            Specification<T>... specifications) {
        return compile(SearchCompilationMode.PAGE, filter, query, pageable, definition, specifications);
    }

    /**
     * Compiles a request intended for a slice query without a count.
     *
     * @param filter optional RSQL filter
     * @param query optional free-text query
     * @param pageable requested page and sort
     * @param definition allowed search capabilities
     * @param specifications mandatory application specifications combined with {@code AND}
     * @param <T> entity type
     * @return validated specification and pageable
     */
    @SafeVarargs
    public final <T> CompiledSearch<T> compileSlice(
            String filter,
            String query,
            Pageable pageable,
            SearchDefinition<T> definition,
            Specification<T>... specifications) {
        return compile(SearchCompilationMode.SLICE, filter, query, pageable, definition, specifications);
    }

    @SafeVarargs
    private final <T> CompiledSearch<T> compile(
            SearchCompilationMode mode,
            String filter,
            String query,
            Pageable pageable,
            SearchDefinition<T> definition,
            Specification<T>... specifications) {
        Objects.requireNonNull(definition, "definition must not be null");
        rsqlSearchGuard.validateDefinition(definition);
        SearchProtectionContext protection =
                new SearchProtectionContext(definition.effectiveLimits(policy), mode);
        Specification<T> specification =
                specification(filter, query, definition, protection, specifications);
        Pageable validatedPageable =
                searchPageableGuard.pageable(pageable, definition, protection);
        if (SearchSpecificationSorting.requiresCriteriaSorting(pageable, definition)) {
            specification = SearchSpecificationSorting.apply(
                    specification,
                    pageable.getSort(),
                    definition);
            validatedPageable = SearchSpecificationSorting.withoutSort(validatedPageable);
        }
        protection.completeRequest();
        return new CompiledSearch<>(specification, validatedPageable);
    }

    @SafeVarargs
    private final <T> Specification<T> specification(
            String filter,
            String query,
            SearchDefinition<T> definition,
            SearchProtectionContext protection,
            Specification<T>... specifications) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(specifications, "specifications must not be null");

        List<Specification<T>> merged = new ArrayList<>(specifications.length + 2);
        for (Specification<T> specification : specifications) {
            merged.add(Objects.requireNonNull(
                    specification,
                "specifications must not contain null values"));
        }
        merged.add(filterSpecification(filter, definition, protection));
        merged.add(searchQueryGuard.specification(query, definition, protection));
        return Specification.allOf(merged);
    }

    private <T> Specification<T> filterSpecification(
            String filter,
            SearchDefinition<T> definition,
            SearchProtectionContext protection) {
        if (!StringUtils.hasText(filter)) {
            protection.completeFilter();
            return Specification.unrestricted();
        }
        return rsqlSearchGuard.specification(filter, definition, protection);
    }
}
