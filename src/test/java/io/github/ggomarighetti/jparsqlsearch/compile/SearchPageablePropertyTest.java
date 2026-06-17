package io.github.ggomarighetti.jparsqlsearch.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.integration.bench.domain.Product;
import io.github.ggomarighetti.jparsqlsearch.property.PageableInputGenerator;
import io.github.ggomarighetti.jparsqlsearch.property.SearchPropertyFixtures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SearchPageablePropertyTest {
    private static final int RANDOM_TRIES = 2_500;

    @Test
    void arbitraryValidSpringPageableNeverEscapesUnexpectedThrowable() {
        SearchPageableGuard guard = new SearchPageableGuard();
        SearchDefinition<Product> definition = SearchPropertyFixtures.pageableDefinition();
        PageableInputGenerator generator = new PageableInputGenerator(0xA08_101L);

        for (int i = 0; i < RANDOM_TRIES; i++) {
            Pageable pageable = generator.nextPageable();
            try {
                guard.pageable(pageable, definition);
            } catch (Throwable throwable) {
                if (!SearchPropertyFixtures.isExpectedPageableThrowable(throwable)) {
                    fail("Unexpected throwable for pageable [" + pageable + "]", throwable);
                }
            }
        }
    }

    @Test
    void acceptedSortsTranslatePublicSelectorsToInternalPaths() {
        SearchPageableGuard guard = new SearchPageableGuard();
        SearchDefinition<Product> definition = SearchPropertyFixtures.pageableDefinition();
        PageableInputGenerator generator = new PageableInputGenerator(0xA08_102L);

        for (int i = 0; i < RANDOM_TRIES; i++) {
            Sort source = generator.nextSort();
            if (!isPotentiallyAcceptable(source)) {
                continue;
            }
            Sort translated;
            try {
                translated = guard.sort(source, definition);
            } catch (Throwable throwable) {
                if (SearchPropertyFixtures.isExpectedPageableThrowable(throwable)) {
                    continue;
                }
                fail("Unexpected throwable for sort [" + source + "]", throwable);
                return;
            }
            assertTranslated(source.toList(), translated.toList());
        }
    }

    private static boolean isPotentiallyAcceptable(Sort source) {
        Set<String> selectors = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (Sort.Order order : source) {
            String path = SearchPropertyFixtures.SORTING_PATHS.get(order.getProperty());
            if (path == null) {
                return false;
            }
            if (!selectors.add(order.getProperty()) || !paths.add(path)) {
                return false;
            }
            if ("releaseDate".equals(order.getProperty()) && order.getDirection() != Sort.Direction.DESC) {
                return false;
            }
            if ("amount".equals(order.getProperty())
                    && (order.isIgnoreCase() || order.getNullHandling() != Sort.NullHandling.NATIVE)) {
                return false;
            }
            if ("legacyAmount".equals(order.getProperty())
                    && (order.isIgnoreCase() || order.getNullHandling() != Sort.NullHandling.NATIVE)) {
                return false;
            }
            if ("releaseDate".equals(order.getProperty())
                    && (order.isIgnoreCase() || order.getNullHandling() != Sort.NullHandling.NATIVE)) {
                return false;
            }
            if ("supplierName".equals(order.getProperty())
                    && order.getNullHandling() != Sort.NullHandling.NATIVE) {
                return false;
            }
        }
        return true;
    }

    private static void assertTranslated(List<Sort.Order> source, List<Sort.Order> translated) {
        assertEquals(source.size(), translated.size());
        List<String> translatedProperties = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Sort.Order sourceOrder = source.get(i);
            Sort.Order translatedOrder = translated.get(i);
            assertEquals(SearchPropertyFixtures.SORTING_PATHS.get(sourceOrder.getProperty()), translatedOrder.getProperty());
            assertEquals(sourceOrder.getDirection(), translatedOrder.getDirection());
            assertEquals(sourceOrder.isIgnoreCase(), translatedOrder.isIgnoreCase());
            assertEquals(sourceOrder.getNullHandling(), translatedOrder.getNullHandling());
            translatedProperties.add(translatedOrder.getProperty());
        }
        assertEquals(translatedProperties.size(), new HashSet<>(translatedProperties).size());
    }
}
