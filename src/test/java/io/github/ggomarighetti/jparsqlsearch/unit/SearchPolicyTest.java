package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SearchPolicyTest {
    @Test
    void nestedBuildersCreateDefaultPolicyGroups() {
        SearchPolicy defaults = SearchPolicy.defaults();

        assertEquals(defaults.rsql(), SearchPolicy.Rsql.builder().build());
        assertEquals(defaults.filter(), SearchPolicy.Filter.builder().build());
        assertEquals(defaults.filter().like(), SearchPolicy.Filter.Like.builder().build());
        assertEquals(defaults.paging(), SearchPolicy.Paging.builder().build());
        assertEquals(defaults.paging().page(), SearchPolicy.Paging.Page.builder().build());
        assertEquals(defaults.paging().slice(), SearchPolicy.Paging.Slice.builder().build());
        assertEquals(defaults.sorting(), SearchPolicy.Sorting.builder().build());
        assertEquals(defaults.query(), SearchPolicy.Query.builder().build());
        assertEquals(defaults.paths(), SearchPolicy.Paths.builder().build());
    }

    @Test
    void standaloneNestedBuildersRecordSetterOverrides() {
        assertEquals(7, SearchPolicy.Filter.builder().maxComparisons(7).build().maxComparisons());
        assertEquals(
                8,
                SearchPolicy.Filter.Like.builder().maxPatternLength(8).build().maxPatternLength());
        assertEquals(
                2,
                SearchPolicy.Paging.Page.builder().maxToManyPaths(2).build().maxToManyPaths());
        assertEquals(
                9,
                SearchPolicy.Paging.Slice.builder().maxSize(9).build().maxSize());
        assertEquals(4, SearchPolicy.Paths.builder().maxDepth(4).build().maxDepth());
    }

    @Test
    void rejectsInvalidNumericLimits() {
        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Rsql.builder().maxLength(0).build()));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Sorting.builder().maxRelationOrders(-1).build());
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().maxOffset(-1).build());
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().minPage(2).maxPage(1).build());
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().minSize(20).maxSize(10).build());
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().unpagedSize(20).maxUnpagedSize(10).build());
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPolicy.Query.builder().minLength(5).maxLength(4).build());
    }
}
