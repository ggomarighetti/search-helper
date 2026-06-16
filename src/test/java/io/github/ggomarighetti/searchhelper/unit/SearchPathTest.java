package io.github.ggomarighetti.searchhelper.unit;

import io.github.ggomarighetti.searchhelper.definition.SearchPath;
import io.github.ggomarighetti.searchhelper.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.searchhelper.policy.SearchPolicy;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPathTest {
    private static final SearchPolicy.Paths DEEP_PATHS = new SearchPolicy.Paths(6);

    @Test
    void validateUsesDefaultPathLimits() {
        SearchPath.validate(TestTypes.Product.class, "amount", "price", BigDecimal.class);
    }

    @Test
    void resolvesDirectFieldsSetterOnlyPropertiesAndInheritedFields() {
        SearchPath.Metadata fieldOnly = SearchPath.metadata(
                FieldOnlyRoot.class,
                "code",
                "code",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata setterOnly = SearchPath.metadata(
                SetterOnlyRoot.class,
                "label",
                "writeOnly.label",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata inherited = SearchPath.metadata(
                ChildFieldRoot.class,
                "inherited",
                "inherited",
                String.class,
                DEEP_PATHS);

        assertEquals(String.class, fieldOnly.type());
        assertEquals(String.class, setterOnly.type());
        assertEquals(String.class, inherited.type());
    }

    @Test
    void resolvesCollectionElementTypesFromArraysMapsAndIterableHierarchy() {
        assertCollectionPath("arrayLines.sku");
        assertCollectionPath("linesBySku.sku");
        assertCollectionPath("lineBag.sku");
        assertCollectionPath("lineIterable.sku");
        assertCollectionPath("wildcardLines.sku");
    }

    @Test
    void reportsTerminalCollectionMetadataWithoutTraversingThroughIt() {
        SearchPath.Metadata array = SearchPath.metadata(
                CollectionRoot.class,
                "arrayLines",
                "arrayLines",
                Line[].class,
                DEEP_PATHS);
        SearchPath.Metadata map = SearchPath.metadata(
                CollectionRoot.class,
                "linesBySku",
                "linesBySku",
                Map.class,
                DEEP_PATHS);

        assertEquals(Line[].class, array.type());
        assertFalse(array.traversesCollection());
        assertTrue(array.collectionValued());
        assertEquals(Map.class, map.type());
        assertFalse(map.traversesCollection());
        assertTrue(map.collectionValued());
    }

    @Test
    void rejectsCollectionPathsWhenElementTypeCannotResolveToAPropertyOwner() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "genericLines.sku", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "nestedLines.sku", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "arrayLines.sku", String.class, DEEP_PATHS));
    }

    @Test
    void rejectsBlankMissingAndTooDeepSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", "customer..name", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "missing", "missing", String.class, DEEP_PATHS));

        SearchDefinitionValidationException exception = assertThrows(
                SearchDefinitionValidationException.class,
                () -> SearchPath.metadata(
                        TestTypes.Product.class,
                        "countryCode",
                        "customer.region.country.code",
                        String.class,
                        new SearchPolicy.Paths(3)));

        assertEquals(SearchDefinitionValidationException.PATH_LIMIT_EXCEEDED, exception.code());
    }

    @Test
    void derivesTopologyFromJpaAnnotationsAndEntityTargets() {
        assertTopology("buyer.sku", Set.of("buyer"), Set.of());
        assertTopology("invoice.number", Set.of("invoice"), Set.of());
        assertTopology("entityTarget.name", Set.of("entityTarget"), Set.of());
        assertTopology("orders.number", Set.of("orders"), Set.of("orders"));
        assertTopology("tags.label", Set.of("tags"), Set.of("tags"));

        SearchPath.Topology labels = SearchPath.topology(TopologyRoot.class, "labels", "labels", DEEP_PATHS);
        assertEquals(Set.of("labels"), labels.joinedPaths());
        assertEquals(Set.of("labels"), labels.toManyPaths());
    }

    @Test
    void topologyDefensivelyCopiesSetsAndExposesEmptyTopology() {
        Set<String> joinedPaths = new java.util.LinkedHashSet<>();
        joinedPaths.add("buyer");
        Set<String> toManyPaths = new java.util.LinkedHashSet<>();
        toManyPaths.add("orders");

        SearchPath.Topology topology = new SearchPath.Topology(2, joinedPaths, toManyPaths);
        joinedPaths.clear();
        toManyPaths.clear();

        assertEquals(Set.of("buyer"), topology.joinedPaths());
        assertEquals(Set.of("orders"), topology.toManyPaths());
        assertThrows(UnsupportedOperationException.class, () -> topology.joinedPaths().add("other"));
        assertEquals(new SearchPath.Topology(0, Set.of(), Set.of()), SearchPath.Topology.none());
    }

    private static void assertCollectionPath(String path) {
        SearchPath.Metadata metadata = SearchPath.metadata(CollectionRoot.class, "sku", path, String.class, DEEP_PATHS);

        assertEquals(String.class, metadata.type());
        assertTrue(metadata.traversesCollection());
        assertFalse(metadata.collectionValued());
    }

    private static void assertTopology(String path, Set<String> joinedPaths, Set<String> toManyPaths) {
        SearchPath.Topology topology = SearchPath.topology(TopologyRoot.class, path, path, DEEP_PATHS);

        assertEquals(joinedPaths, topology.joinedPaths());
        assertEquals(toManyPaths, topology.toManyPaths());
    }

    private static final class FieldOnlyRoot {
        private String code;
    }

    private static class BaseFieldRoot {
        private String inherited;
    }

    private static final class ChildFieldRoot extends BaseFieldRoot {
    }

    private static final class SetterOnlyRoot {
        @SuppressWarnings("unused")
        private WriteOnly writeOnly;

        public void setWriteOnly(WriteOnly writeOnly) {
            this.writeOnly = writeOnly;
        }
    }

    private static final class WriteOnly {
        public String getLabel() {
            return null;
        }
    }

    private static final class CollectionRoot {
        public Line[] getArrayLines() {
            return new Line[0];
        }

        public Map<String, Line> getLinesBySku() {
            return Map.of();
        }

        public LineBag getLineBag() {
            return new LineBag();
        }

        public LineIterable getLineIterable() {
            return new LineIterable();
        }

        public List<? extends Line> getWildcardLines() {
            return List.of();
        }
    }

    private static final class GenericRoot<T extends Line> {
        public List<T> getGenericLines() {
            return List.of();
        }

        public List<List<Line>> getNestedLines() {
            return List.of();
        }

        public List<T[]> getArrayLines() {
            return List.of();
        }
    }

    private static final class Line {
        public String getSku() {
            return null;
        }
    }

    private static final class LineBag extends ArrayList<Line> {
    }

    private static final class LineIterable implements Iterable<Line> {
        @Override
        public Iterator<Line> iterator() {
            return List.<Line>of().iterator();
        }
    }

    @Entity
    private static final class EntityTarget {
        public String getName() {
            return null;
        }
    }

    private static final class Invoice {
        public String getNumber() {
            return null;
        }
    }

    private static final class Order {
        public String getNumber() {
            return null;
        }
    }

    private static final class Tag {
        public String getLabel() {
            return null;
        }
    }

    private static final class TopologyRoot {
        @ManyToOne
        private Line buyer;

        @OneToMany
        private List<Order> orders;

        @ManyToMany
        private Set<Tag> tags;

        @ElementCollection
        private List<String> labels;

        public Line getBuyer() {
            return buyer;
        }

        @OneToOne
        public Invoice getInvoice() {
            return null;
        }

        public EntityTarget getEntityTarget() {
            return null;
        }

        public List<Order> getOrders() {
            return orders;
        }

        public Set<Tag> getTags() {
            return tags;
        }

        public List<String> getLabels() {
            return labels;
        }
    }
}
