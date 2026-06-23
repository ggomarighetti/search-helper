package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators;
import io.github.ggomarighetti.jparsqlsearch.sort.SearchSorting;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Mutable per-request protection state shared by the guards while a search is
 * compiled. Applications normally interact with {@link io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler}.
 */
final class SearchProtectionContext {
    private final SearchPolicy policy;
    private final Mode mode;
    private final SearchPolicy.Filter filterLimits;
    private final SearchPolicy.Sorting sortingLimits;
    private final SearchPolicy.Query queryLimits;
    private final SearchTextPatternGuard textPatternGuard;
    private final Set<String> joinedPaths = new LinkedHashSet<>();
    private final Set<String> toManyPaths = new LinkedHashSet<>();
    private final Set<String> sortingJoinedPaths = new LinkedHashSet<>();
    private final Map<String, Integer> comparisonsBySelector = new LinkedHashMap<>();

    private int comparisons;
    private int totalArguments;
    private int totalOrBranches;
    private int negatedComparisons;
    private int betweenRanges;
    private int relationSortOrders;
    private boolean distinct;
    private boolean queryPresent;
    private boolean unpagedInput;
    private boolean paged;

    public SearchProtectionContext(SearchPolicy policy, Mode mode) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.filterLimits = policy.filter();
        this.sortingLimits = policy.sorting();
        this.queryLimits = policy.query();
        this.textPatternGuard = new SearchTextPatternGuard(filterLimits.text());
    }

    public SearchPolicy policy() {
        return policy;
    }

    public Mode mode() {
        return mode;
    }

    public void recordComparison(
            SearchField<?> field,
            RsqlOperatorDescriptor operator,
            int argumentCount,
            Iterable<String> arguments) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");

        comparisons++;
        requireAtMost("filter.max-comparisons", comparisons, filterLimits.maxComparisons());
        comparisonsBySelector.merge(field.selector(), 1, Integer::sum);
        requireAtMost(
                "filter.max-comparisons-per-selector",
                comparisonsBySelector.get(field.selector()),
                filterLimits.maxComparisonsPerSelector());

        totalArguments += argumentCount;
        requireAtMost(
                "filter.max-arguments-per-comparison",
                argumentCount,
                filterLimits.maxArgumentsPerComparison());
        requireAtMost("filter.max-arguments-total", totalArguments, filterLimits.maxArgumentsTotal());
        for (String argument : arguments) {
            requireAtMost("filter.max-argument-length", argument.length(), filterLimits.maxArgumentLength());
        }

        RsqlOperator rsqlOperator = operator.operator();
        if (RsqlOperators.IN.equals(rsqlOperator)) {
            requireAtMost("filter.max-in-values", argumentCount, filterLimits.maxInValues());
        }
        if (RsqlOperators.NOT_IN.equals(rsqlOperator)) {
            requireAtMost("filter.max-not-in-values", argumentCount, filterLimits.maxNotInValues());
        }
        if (RsqlOperators.BETWEEN.equals(rsqlOperator) || RsqlOperators.NOT_BETWEEN.equals(rsqlOperator)) {
            betweenRanges++;
            requireAtMost("filter.max-between-ranges", betweenRanges, filterLimits.maxBetweenRanges());
        }
        if (isNegated(rsqlOperator)) {
            negatedComparisons++;
            requireAtMost(
                    "filter.max-negated-comparisons",
                    negatedComparisons,
                    filterLimits.maxNegatedComparisons());
        }
        textPatternGuard.validate(rsqlOperator, arguments);
        recordFilterTopology(field.filtering().topology());
    }

    public void recordOr(int branches, int selectorCount, int joinRootCount) {
        totalOrBranches += branches;
        requireAtMost("filter.max-or-branches", totalOrBranches, filterLimits.maxOrBranches());
        requireAtMost("filter.max-or-selectors", selectorCount, filterLimits.maxOrSelectors());
        requireAtMost("filter.max-or-join-roots", joinRootCount, filterLimits.maxOrJoinRoots());
        if (selectorCount > 1) {
            requireAtMost(
                    "filter.max-heterogeneous-or-branches",
                    branches,
                    filterLimits.maxHeterogeneousOrBranches());
        }
    }

    public void recordDistinct() {
        distinct = true;
    }

    public void completeFilter() {
        validateFilterTopology();
        if (filterLimits.requireDistinctForToMany() && !toManyPaths.isEmpty() && !distinct) {
            throw exceeded("filter.require-distinct-for-to-many", 0, 1);
        }
    }

    public void recordQuery(String query) {
        if (query == null) {
            return;
        }
        queryPresent = true;
        requireAtMost("query.max-length", query.length(), queryLimits.maxLength());
        if (query.length() < queryLimits.minLength()) {
            throw exceeded("query.min-length", query.length(), queryLimits.minLength());
        }
    }

    public void completeQuery() {
        // Query-only limits are enforced when the query is recorded. Cross
        // request combinations are checked in completeRequest().
    }

    public void recordSort(SearchSorting sorting, Sort.Order order) {
        Objects.requireNonNull(sorting, "sorting must not be null");
        Objects.requireNonNull(order, "order must not be null");
        SearchPath.Topology topology = sorting.topology();
        if (!topology.toManyPaths().isEmpty() && sortingLimits.disallowToManySorting()) {
            throw exceeded("sorting.disallow-to-many-sorting", 1, 0);
        }
        if (!topology.joinedPaths().isEmpty()) {
            if (!sortingLimits.allowRelationSorting()) {
                throw exceeded("sorting.allow-relation-sorting", 1, 0);
            }
            relationSortOrders++;
            requireAtMost(
                    "sorting.max-relation-orders",
                    relationSortOrders,
                    sortingLimits.maxRelationOrders());
        }
        if (order.isIgnoreCase() && !sortingLimits.allowIgnoreCase()) {
            throw exceeded("sorting.allow-ignore-case", 1, 0);
        }
        if (order.getNullHandling() != Sort.NullHandling.NATIVE && !sortingLimits.allowNullHandling()) {
            throw exceeded("sorting.allow-null-handling", 1, 0);
        }
        for (String path : topology.joinedPaths()) {
            sortingJoinedPaths.add(path);
        }
        requireAtMost(
                "sorting.max-joined-paths",
                sortingJoinedPaths.size(),
                sortingLimits.maxJoinedPaths());
    }

    public void recordPaging(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");
        if (pageable.isUnpaged()) {
            unpagedInput = true;
            return;
        }
        paged = true;
        if (mode == Mode.SLICE) {
            if (!policy.paging().slice().enabled()) {
                throw exceeded("paging.slice.enabled", 1, 0);
            }
            requireAtMost("paging.slice.max-size", pageable.getPageSize(), policy.paging().slice().maxSize());
        }
    }

    public void completePageable() {
        // Pageable-only limits are enforced by PageableSearchGuard and recordSort.
    }

    public void completeRequest() {
        validateFilterTopology();
        validatePageRequest();
        validateQueryRequest();
    }

    public int joinedPaths() {
        return joinedPaths.size();
    }

    public int toManyPaths() {
        return toManyPaths.size();
    }

    public boolean distinct() {
        return distinct;
    }

    private void recordFilterTopology(SearchPath.Topology topology) {
        for (String path : topology.joinedPaths()) {
            joinedPaths.add(path);
        }
        for (String path : topology.toManyPaths()) {
            toManyPaths.add(path);
        }
        validateFilterTopology();
    }

    private void validateFilterTopology() {
        requireAtMost("filter.max-joined-paths", joinedPaths.size(), filterLimits.maxJoinedPaths());
        requireAtMost("filter.max-to-many-paths", toManyPaths.size(), filterLimits.maxToManyPaths());
        if (!filterLimits.allowToManyFiltering() && !toManyPaths.isEmpty()) {
            throw exceeded("filter.allow-to-many-filtering", toManyPaths.size(), 0);
        }
    }

    private void validatePageRequest() {
        if (mode != Mode.PAGE || !paged) {
            return;
        }
        SearchPolicy.Paging.Page pageLimits = policy.paging().page();
        if (!pageLimits.allowToManyCount() && !toManyPaths.isEmpty()) {
            throw exceeded("paging.page.allow-to-many-count", toManyPaths.size(), 0);
        }
        requireAtMost("paging.page.max-to-many-paths", toManyPaths.size(), pageLimits.maxToManyPaths());
        if (!pageLimits.allowDistinctCount() && distinct) {
            throw exceeded("paging.page.allow-distinct-count", 1, 0);
        }
        requireAtMost("paging.page.max-joined-paths", joinedPaths.size(), pageLimits.maxJoinedPaths());
    }

    private void validateQueryRequest() {
        if (!queryPresent) {
            return;
        }
        if (!queryLimits.allowWithToManyFilter() && !toManyPaths.isEmpty()) {
            throw exceeded("query.allow-with-to-many-filter", toManyPaths.size(), 0);
        }
        if (!queryLimits.allowWithRelationSort() && relationSortOrders > 0) {
            throw exceeded("query.allow-with-relation-sort", relationSortOrders, 0);
        }
        if (!queryLimits.allowWithUnpaged() && unpagedInput) {
            throw exceeded("query.allow-with-unpaged", 1, 0);
        }
    }

    private static boolean isNegated(RsqlOperator operator) {
        return RsqlOperators.NOT_EQUAL.equals(operator)
                || RsqlOperators.NOT_IN.equals(operator)
                || RsqlOperators.NOT_NULL.equals(operator)
                || RsqlOperators.NOT_LIKE.equals(operator)
                || RsqlOperators.IGNORE_CASE_NOT_LIKE.equals(operator)
                || RsqlOperators.NOT_BETWEEN.equals(operator);
    }

    private static void requireAtMost(String rule, long actual, long limit) {
        if (actual > limit) {
            throw exceeded(rule, actual, limit);
        }
    }

    private static SearchProtectionException exceeded(String rule, long actual, long limit) {
        return new SearchProtectionException(rule, actual, limit);
    }

    enum Mode {
        PAGE,
        SLICE
    }
}
