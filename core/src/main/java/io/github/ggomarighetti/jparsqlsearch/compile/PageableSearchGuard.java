package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.page.validation.SearchPageableValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class PageableSearchGuard {
    private static final String DEFINITION_MUST_NOT_BE_NULL = "definition must not be null";
    private static final String PAGEABLE_MUST_NOT_BE_NULL = "pageable must not be null";
    private static final String POLICY_MUST_NOT_BE_NULL = "policy must not be null";

    private final SearchPolicy policy;

    PageableSearchGuard() {
        this(SearchPolicy.defaults());
    }

    PageableSearchGuard(SearchPolicy policy) {
        this.policy = Objects.requireNonNull(policy, POLICY_MUST_NOT_BE_NULL);
    }

    Pageable pageable(Pageable pageable, SearchDefinition<?> definition) {
        Objects.requireNonNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        SearchPolicy effectivePolicy = effectivePolicy(definition);
        return pageable(
                pageable,
                definition,
                new SearchProtectionContext(effectivePolicy, SearchProtectionContext.Mode.PAGE));
    }

    Pageable pageable(
            Pageable pageable,
            SearchDefinition<?> definition,
            SearchProtectionContext protection) {
        Objects.requireNonNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(protection, "protection must not be null");
        SearchPolicy effectivePolicy = protection.policy();

        Sort sort = sort(pageable.getSort(), definition, effectivePolicy, protection);
        if (pageable.isUnpaged()) {
            if (!effectivePolicy.paging().allowUnpaged()) {
                throw pageLimitExceeded();
            }
            protection.recordPaging(pageable);
            Pageable validated = PageRequest.of(0, effectivePolicy.paging().defaultUnpagedSize(), sort);
            protection.recordPaging(validated);
            protection.completePageable();
            return validated;
        }
        if (!acceptsPageable(pageable, effectivePolicy.paging())) {
            throw pageLimitExceeded();
        }
        if (!definition.paging().enabled()) {
            throw pageRulesForbidden();
        }
        List<RuleViolation> violations = new ArrayList<>();
        definition.paging().pageViolations(pageable.getPageNumber()).stream()
                .map(violation -> violation.prefixed("page"))
                .forEach(violations::add);
        definition.paging().sizeViolations(pageable.getPageSize()).stream()
                .map(violation -> violation.prefixed("size"))
                .forEach(violations::add);
        if (!violations.isEmpty()) {
            throw pageRulesForbidden(violations);
        }
        Pageable validated = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        protection.recordPaging(validated);
        protection.completePageable();
        return validated;
    }

    Sort sort(Sort sort, SearchDefinition<?> definition) {
        Objects.requireNonNull(sort, "sort must not be null");
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        SearchProtectionContext protection = new SearchProtectionContext(
                effectivePolicy(definition), SearchProtectionContext.Mode.PAGE);
        Sort validated = sort(sort, definition, protection.policy(), protection);
        protection.completePageable();
        return validated;
    }

    private Sort sort(
            Sort sort,
            SearchDefinition<?> definition,
            SearchPolicy effectivePolicy,
            SearchProtectionContext protection) {
        if (sort.isUnsorted()) {
            return Sort.unsorted();
        }

        List<Sort.Order> sourceOrders = sort.toList();
        if (sourceOrders.size() > effectivePolicy.sorting().maxOrders()) {
            throw sortLimitExceeded();
        }
        Set<String> selectors = new LinkedHashSet<>();
        Set<String> paths = new LinkedHashSet<>();
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sourceOrders) {
            if (!selectors.add(order.getProperty())) {
                throw sortLimitExceeded();
            }
            SearchField<?> field =
                    definition.field(order.getProperty()).orElseThrow(this::sortRulesForbidden);
            SearchSorting sorting = field.sorting();
            if (!sorting.accepts(order.getDirection())) {
                throw sortRulesForbidden();
            }
            if (!sorting.acceptsIgnoreCase(order.isIgnoreCase())
                    || !sorting.acceptsNullHandling(order.getNullHandling())) {
                throw sortLimitExceeded();
            }
            if (!paths.add(sorting.path())) {
                throw sortLimitExceeded();
            }
            protection.recordSort(sorting, order);
            orders.add(order.withProperty(sorting.path()));
        }
        return Sort.by(orders);
    }

    private boolean acceptsPageable(Pageable pageable, SearchPolicy.Paging limits) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        long offset = (long) page * size;
        return page >= limits.minPage()
                && page <= limits.maxPage()
                && size >= limits.minSize()
                && size <= limits.maxSize()
                && offset <= limits.maxOffset();
    }

    private SearchPolicy effectivePolicy(SearchDefinition<?> definition) {
        return definition.effectiveLimits(policy);
    }

    private SearchPageableValidationException sortRulesForbidden() {
        return new SearchPageableValidationException(
                SearchPageableValidationException.SORT_RULES_FORBIDDEN,
                "Pageable sort uses fields or directions that are not allowed.");
    }

    private SearchPageableValidationException pageRulesForbidden() {
        return new SearchPageableValidationException(
                SearchPageableValidationException.PAGE_RULES_FORBIDDEN,
                "Pageable page or size uses values that are not allowed.");
    }

    private SearchPageableValidationException pageRulesForbidden(List<RuleViolation> violations) {
        return new SearchPageableValidationException(
                SearchPageableValidationException.PAGE_RULES_FORBIDDEN,
                "Pageable page or size failed validation with %d violation(s)."
                        .formatted(violations.size()),
                violations);
    }

    private SearchPageableValidationException sortLimitExceeded() {
        return new SearchPageableValidationException(
                SearchPageableValidationException.SORT_LIMIT_EXCEEDED,
                "Pageable sort exceeds configured safety limits.");
    }

    private SearchPageableValidationException pageLimitExceeded() {
        return new SearchPageableValidationException(
                SearchPageableValidationException.PAGE_LIMIT_EXCEEDED,
                "Pageable page, size, offset, or unpaged mode exceeds configured safety limits.");
    }
}
