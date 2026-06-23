package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.query.SearchQuery;
import io.github.ggomarighetti.jparsqlsearch.query.validation.SearchQueryValidationException;
import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

final class QuerySearchGuard {
    private static final String DEFINITION_MUST_NOT_BE_NULL = "definition must not be null";
    private static final String POLICY_MUST_NOT_BE_NULL = "policy must not be null";

    private final SearchPolicy policy;

    QuerySearchGuard() {
        this(SearchPolicy.defaults());
    }

    QuerySearchGuard(SearchPolicy policy) {
        this.policy = Objects.requireNonNull(policy, POLICY_MUST_NOT_BE_NULL);
    }

    <T> Specification<T> specification(String query, SearchDefinition<T> definition) {
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        SearchProtectionContext protection = new SearchProtectionContext(
                effectivePolicy(definition), SearchProtectionContext.Mode.PAGE);
        return specification(query, definition, protection);
    }

    <T> Specification<T> specification(
            String query,
            SearchDefinition<T> definition,
            SearchProtectionContext protection) {
        Objects.requireNonNull(definition, DEFINITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(protection, "protection must not be null");
        if (!StringUtils.hasText(query)) {
            protection.completeQuery();
            return Specification.unrestricted();
        }
        SearchPolicy.Query limits = protection.policy().query();
        if (!limits.enabled()) {
            throw rulesForbidden();
        }
        protection.recordQuery(query);
        SearchQuery<T> searchQuery = definition.query();
        if (!searchQuery.enabled()
                || (limits.requireValidator() && !searchQuery.hasRules())) {
            throw rulesForbidden();
        }
        List<RuleViolation> violations = searchQuery.violations(query).stream()
                .map(violation -> violation.prefixed("query"))
                .toList();
        if (!violations.isEmpty()) {
            throw rulesForbidden(violations);
        }
        protection.completeQuery();
        try {
            Specification<T> specification = searchQuery.toSpecification(query);
            if (specification == null) {
                throw rulesForbidden();
            }
            return guardDeferredFailures(specification);
        } catch (SearchQueryValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SearchQueryValidationException(
                    SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                    "Search query could not be compiled to a JPA Specification.",
                    exception);
        }
    }

    private SearchQueryValidationException rulesForbidden() {
        return new SearchQueryValidationException(
                SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                "Search query uses values that are not allowed.");
    }

    private SearchQueryValidationException rulesForbidden(List<RuleViolation> violations) {
        return new SearchQueryValidationException(
                SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                "Search query failed validation with %d violation(s).".formatted(violations.size()),
                violations);
    }

    private SearchPolicy effectivePolicy(SearchDefinition<?> definition) {
        return definition.effectiveLimits(policy);
    }

    private static <T> Specification<T> guardDeferredFailures(Specification<T> specification) {
        return (root, query, criteriaBuilder) -> {
            try {
                return specification.toPredicate(root, query, criteriaBuilder);
            } catch (SearchQueryValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new SearchQueryValidationException(
                        SearchQueryValidationException.QUERY_RULES_FORBIDDEN,
                        "Search query could not be compiled to a JPA Predicate.",
                        exception);
            }
        };
    }
}
