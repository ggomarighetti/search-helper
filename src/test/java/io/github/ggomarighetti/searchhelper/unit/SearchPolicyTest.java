package io.github.ggomarighetti.searchhelper.unit;

import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsInvalidNumericLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Rsql.builder().maxLength(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Sorting.builder().maxRelationOrders(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().maxOffset(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().minPage(2).maxPage(1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().minSize(20).maxSize(10).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Paging.builder().unpagedSize(20).maxUnpagedSize(10).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPolicy.Query.builder().minLength(5).maxLength(4).build());
    }
}
