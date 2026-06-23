package io.github.ggomarighetti.jparsqlsearch.autoconfigure;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaRsqlSearchPropertiesTest {
    @Test
    void bindsEveryPropertyGroupIntoPolicyAndBackendOptions() {
        JpaRsqlSearchProperties properties = bind(Map.ofEntries(
                entry("jpa.rsql.search.rsql.enabled", "false"),
                entry("jpa.rsql.search.rsql.perplexhub.strict-equality", "false"),
                entry("jpa.rsql.search.rsql.perplexhub.like-escape-character", "!"),
                entry("jpa.rsql.search.rsql.max-length", "101"),
                entry("jpa.rsql.search.rsql.max-parentheses-depth", "4"),
                entry("jpa.rsql.search.rsql.max-nodes", "20"),
                entry("jpa.rsql.search.rsql.max-depth", "5"),
                entry("jpa.rsql.search.rsql.max-logical-children", "6"),
                entry("jpa.rsql.search.filter.max-comparisons", "7"),
                entry("jpa.rsql.search.filter.max-comparisons-per-selector", "3"),
                entry("jpa.rsql.search.filter.max-arguments-per-comparison", "4"),
                entry("jpa.rsql.search.filter.max-arguments-total", "15"),
                entry("jpa.rsql.search.filter.max-argument-length", "21"),
                entry("jpa.rsql.search.filter.max-in-values", "5"),
                entry("jpa.rsql.search.filter.max-not-in-values", "6"),
                entry("jpa.rsql.search.filter.max-between-ranges", "2"),
                entry("jpa.rsql.search.filter.max-negated-comparisons", "1"),
                entry("jpa.rsql.search.filter.max-or-branches", "8"),
                entry("jpa.rsql.search.filter.max-or-selectors", "4"),
                entry("jpa.rsql.search.filter.max-or-join-roots", "3"),
                entry("jpa.rsql.search.filter.max-heterogeneous-or-branches", "2"),
                entry("jpa.rsql.search.filter.max-joined-paths", "6"),
                entry("jpa.rsql.search.filter.max-to-many-paths", "2"),
                entry("jpa.rsql.search.filter.allow-to-many-filtering", "false"),
                entry("jpa.rsql.search.filter.require-distinct-for-to-many", "false"),
                entry("jpa.rsql.search.filter.text.max-pattern-length", "40"),
                entry("jpa.rsql.search.filter.text.min-literal-length", "2"),
                entry("jpa.rsql.search.filter.text.allow-leading-wildcard", "true"),
                entry("jpa.rsql.search.filter.text.allow-trailing-wildcard", "false"),
                entry("jpa.rsql.search.filter.text.allow-contains", "false"),
                entry("jpa.rsql.search.filter.text.max-wildcards", "2"),
                entry("jpa.rsql.search.filter.text.allow-ignore-case", "false"),
                entry("jpa.rsql.search.paging.min-page", "1"),
                entry("jpa.rsql.search.paging.max-page", "20"),
                entry("jpa.rsql.search.paging.min-size", "2"),
                entry("jpa.rsql.search.paging.max-size", "50"),
                entry("jpa.rsql.search.paging.max-offset", "1000"),
                entry("jpa.rsql.search.paging.allow-unpaged", "false"),
                entry("jpa.rsql.search.paging.default-unpaged-size", "25"),
                entry("jpa.rsql.search.paging.max-unpaged-size", "80"),
                entry("jpa.rsql.search.paging.page.allow-to-many-count", "true"),
                entry("jpa.rsql.search.paging.page.max-to-many-paths", "3"),
                entry("jpa.rsql.search.paging.page.allow-distinct-count", "false"),
                entry("jpa.rsql.search.paging.page.max-joined-paths", "4"),
                entry("jpa.rsql.search.paging.slice.enabled", "false"),
                entry("jpa.rsql.search.paging.slice.prefer-for-to-many", "false"),
                entry("jpa.rsql.search.paging.slice.max-size", "30"),
                entry("jpa.rsql.search.sorting.max-orders", "4"),
                entry("jpa.rsql.search.sorting.allow-relation-sorting", "false"),
                entry("jpa.rsql.search.sorting.max-relation-orders", "0"),
                entry("jpa.rsql.search.sorting.allow-ignore-case", "true"),
                entry("jpa.rsql.search.sorting.allow-null-handling", "true"),
                entry("jpa.rsql.search.sorting.max-joined-paths", "5"),
                entry("jpa.rsql.search.sorting.disallow-to-many-sorting", "false"),
                entry("jpa.rsql.search.query.enabled", "false"),
                entry("jpa.rsql.search.query.min-length", "2"),
                entry("jpa.rsql.search.query.max-length", "60"),
                entry("jpa.rsql.search.query.require-validator", "true"),
                entry("jpa.rsql.search.query.allow-with-to-many-filter", "false"),
                entry("jpa.rsql.search.query.allow-with-relation-sort", "false"),
                entry("jpa.rsql.search.query.allow-with-unpaged", "false"),
                entry("jpa.rsql.search.paths.max-depth", "4")));

        SearchPolicy policy = properties.toPolicy();

        assertRsqlProperties(properties, policy);
        assertFilterProperties(properties, policy);
        assertPagingProperties(properties, policy);
        assertSortingProperties(properties, policy);
        assertQueryAndPathProperties(properties, policy);
    }

    private static void assertRsqlProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertFalse(properties.getRsql().isEnabled());
        assertFalse(properties.getRsql().getPerplexhub().isStrictEquality());
        assertEquals('!', properties.getRsql().getPerplexhub().getLikeEscapeCharacter());
        assertEquals(101, properties.getRsql().getMaxLength());
        assertEquals(4, properties.getRsql().getMaxParenthesesDepth());
        assertEquals(20, properties.getRsql().getMaxNodes());
        assertEquals(5, properties.getRsql().getMaxDepth());
        assertEquals(6, properties.getRsql().getMaxLogicalChildren());
        assertEquals(101, policy.rsql().maxLength());
        assertEquals(4, policy.rsql().maxParenthesesDepth());
        assertEquals(20, policy.rsql().maxNodes());
        assertEquals(5, policy.rsql().maxDepth());
        assertEquals(6, policy.rsql().maxLogicalChildren());
    }

    private static void assertFilterProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertEquals(7, properties.getFilter().getMaxComparisons());
        assertEquals(3, properties.getFilter().getMaxComparisonsPerSelector());
        assertEquals(4, properties.getFilter().getMaxArgumentsPerComparison());
        assertEquals(15, properties.getFilter().getMaxArgumentsTotal());
        assertEquals(21, properties.getFilter().getMaxArgumentLength());
        assertEquals(5, properties.getFilter().getMaxInValues());
        assertEquals(6, properties.getFilter().getMaxNotInValues());
        assertEquals(2, properties.getFilter().getMaxBetweenRanges());
        assertEquals(1, properties.getFilter().getMaxNegatedComparisons());
        assertEquals(8, properties.getFilter().getMaxOrBranches());
        assertEquals(4, properties.getFilter().getMaxOrSelectors());
        assertEquals(3, properties.getFilter().getMaxOrJoinRoots());
        assertEquals(2, properties.getFilter().getMaxHeterogeneousOrBranches());
        assertEquals(6, properties.getFilter().getMaxJoinedPaths());
        assertEquals(2, properties.getFilter().getMaxToManyPaths());
        assertFalse(properties.getFilter().isAllowToManyFiltering());
        assertFalse(properties.getFilter().isRequireDistinctForToMany());
        assertEquals(7, policy.filter().maxComparisons());
        assertEquals(3, policy.filter().maxComparisonsPerSelector());
        assertEquals(4, policy.filter().maxArgumentsPerComparison());
        assertEquals(15, policy.filter().maxArgumentsTotal());
        assertEquals(21, policy.filter().maxArgumentLength());
        assertEquals(5, policy.filter().maxInValues());
        assertEquals(6, policy.filter().maxNotInValues());
        assertEquals(2, policy.filter().maxBetweenRanges());
        assertEquals(1, policy.filter().maxNegatedComparisons());
        assertEquals(8, policy.filter().maxOrBranches());
        assertEquals(4, policy.filter().maxOrSelectors());
        assertEquals(3, policy.filter().maxOrJoinRoots());
        assertEquals(2, policy.filter().maxHeterogeneousOrBranches());
        assertEquals(6, policy.filter().maxJoinedPaths());
        assertEquals(2, policy.filter().maxToManyPaths());
        assertFalse(policy.filter().allowToManyFiltering());
        assertFalse(policy.filter().requireDistinctForToMany());

        assertFilterTextProperties(properties, policy);
    }

    private static void assertFilterTextProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertEquals(40, properties.getFilter().getText().getMaxPatternLength());
        assertEquals(2, properties.getFilter().getText().getMinLiteralLength());
        assertTrue(properties.getFilter().getText().isAllowLeadingWildcard());
        assertFalse(properties.getFilter().getText().isAllowTrailingWildcard());
        assertFalse(properties.getFilter().getText().isAllowContains());
        assertEquals(2, properties.getFilter().getText().getMaxWildcards());
        assertFalse(properties.getFilter().getText().isAllowIgnoreCase());
        assertEquals(40, policy.filter().text().maxPatternLength());
        assertEquals(2, policy.filter().text().minLiteralLength());
        assertTrue(policy.filter().text().allowLeadingWildcard());
        assertFalse(policy.filter().text().allowTrailingWildcard());
        assertFalse(policy.filter().text().allowContains());
        assertEquals(2, policy.filter().text().maxWildcards());
        assertFalse(policy.filter().text().allowIgnoreCase());
    }

    private static void assertPagingProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertEquals(1, properties.getPaging().getMinPage());
        assertEquals(20, properties.getPaging().getMaxPage());
        assertEquals(2, properties.getPaging().getMinSize());
        assertEquals(50, properties.getPaging().getMaxSize());
        assertEquals(1000L, properties.getPaging().getMaxOffset());
        assertFalse(properties.getPaging().isAllowUnpaged());
        assertEquals(25, properties.getPaging().getDefaultUnpagedSize());
        assertEquals(80, properties.getPaging().getMaxUnpagedSize());
        assertEquals(1, policy.paging().minPage());
        assertEquals(20, policy.paging().maxPage());
        assertEquals(2, policy.paging().minSize());
        assertEquals(50, policy.paging().maxSize());
        assertEquals(1000L, policy.paging().maxOffset());
        assertFalse(policy.paging().allowUnpaged());
        assertEquals(25, policy.paging().defaultUnpagedSize());
        assertEquals(80, policy.paging().maxUnpagedSize());

        assertPageAndSliceProperties(properties, policy);
    }

    private static void assertPageAndSliceProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertTrue(properties.getPaging().getPage().isAllowToManyCount());
        assertEquals(3, properties.getPaging().getPage().getMaxToManyPaths());
        assertFalse(properties.getPaging().getPage().isAllowDistinctCount());
        assertEquals(4, properties.getPaging().getPage().getMaxJoinedPaths());
        assertTrue(policy.paging().page().allowToManyCount());
        assertEquals(3, policy.paging().page().maxToManyPaths());
        assertFalse(policy.paging().page().allowDistinctCount());
        assertEquals(4, policy.paging().page().maxJoinedPaths());

        assertFalse(properties.getPaging().getSlice().isEnabled());
        assertFalse(properties.getPaging().getSlice().isPreferForToMany());
        assertEquals(30, properties.getPaging().getSlice().getMaxSize());
        assertFalse(policy.paging().slice().enabled());
        assertFalse(policy.paging().slice().preferForToMany());
        assertEquals(30, policy.paging().slice().maxSize());
    }

    private static void assertSortingProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertEquals(4, properties.getSorting().getMaxOrders());
        assertFalse(properties.getSorting().isAllowRelationSorting());
        assertEquals(0, properties.getSorting().getMaxRelationOrders());
        assertTrue(properties.getSorting().isAllowIgnoreCase());
        assertTrue(properties.getSorting().isAllowNullHandling());
        assertEquals(5, properties.getSorting().getMaxJoinedPaths());
        assertFalse(properties.getSorting().isDisallowToManySorting());
        assertEquals(4, policy.sorting().maxOrders());
        assertFalse(policy.sorting().allowRelationSorting());
        assertEquals(0, policy.sorting().maxRelationOrders());
        assertTrue(policy.sorting().allowIgnoreCase());
        assertTrue(policy.sorting().allowNullHandling());
        assertEquals(5, policy.sorting().maxJoinedPaths());
        assertFalse(policy.sorting().disallowToManySorting());
    }

    private static void assertQueryAndPathProperties(JpaRsqlSearchProperties properties, SearchPolicy policy) {
        assertFalse(properties.getQuery().isEnabled());
        assertEquals(2, properties.getQuery().getMinLength());
        assertEquals(60, properties.getQuery().getMaxLength());
        assertTrue(properties.getQuery().isRequireValidator());
        assertFalse(properties.getQuery().isAllowWithToManyFilter());
        assertFalse(properties.getQuery().isAllowWithRelationSort());
        assertFalse(properties.getQuery().isAllowWithUnpaged());
        assertFalse(policy.query().enabled());
        assertEquals(2, policy.query().minLength());
        assertEquals(60, policy.query().maxLength());
        assertTrue(policy.query().requireValidator());
        assertFalse(policy.query().allowWithToManyFilter());
        assertFalse(policy.query().allowWithRelationSort());
        assertFalse(policy.query().allowWithUnpaged());

        assertEquals(4, properties.getPaths().getMaxDepth());
        assertEquals(4, policy.paths().maxDepth());
    }

    private static JpaRsqlSearchProperties bind(Map<String, String> values) {
        JpaRsqlSearchProperties properties = new JpaRsqlSearchProperties();
        new Binder(new MapConfigurationPropertySource(values))
                .bind("jpa.rsql.search", Bindable.ofInstance(properties));
        return properties;
    }
}
