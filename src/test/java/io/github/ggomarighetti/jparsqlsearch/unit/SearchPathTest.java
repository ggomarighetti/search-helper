package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
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
        SearchPath.Metadata indexedOnly = SearchPath.metadata(
                IndexedOnlyRoot.class,
                "values",
                "values",
                String[].class,
                DEEP_PATHS);

        assertEquals(String.class, fieldOnly.type());
        assertEquals(String.class, setterOnly.type());
        assertEquals(String.class, inherited.type());
        assertEquals(String[].class, indexedOnly.type());
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
        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "genericLines.sku", String.class, DEEP_PATHS)));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "nestedLines.sku", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "arrayLines.sku", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "lineArrays.sku", String.class, DEEP_PATHS));
    }

    @Test
    void rejectsBlankMissingAndTooDeepSegments() {
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", "customer..name", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "missing", "missing", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(IndexedOnlyNoFieldRoot.class, "values", "values", String.class, DEEP_PATHS));

        SearchDefinitionValidationException exception = thrownBy(
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
        assertTopology("scalarLabel", Set.of("scalarLabel"), Set.of("scalarLabel"));

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
        thrownBy(UnsupportedOperationException.class, () -> topology.joinedPaths().add("other"));
        assertEquals(new SearchPath.Topology(0, Set.of(), Set.of()), SearchPath.Topology.none());
    }

    @Test
    void privateGenericHelpersHandleExoticReflectiveTypes() throws ReflectiveOperationException {
        Method collectionElementType = SearchPath.class.getDeclaredMethod(
                "collectionElementType",
                Class.class,
                Type.class);
        collectionElementType.setAccessible(true);
        Method genericArgumentFromType = SearchPath.class.getDeclaredMethod(
                "genericArgument",
                Type.class,
                Class.class,
                int.class);
        genericArgumentFromType.setAccessible(true);
        Method genericArgumentFromClass = SearchPath.class.getDeclaredMethod(
                "genericArgument",
                Class.class,
                Class.class,
                int.class);
        genericArgumentFromClass.setAccessible(true);
        Method classFromType = SearchPath.class.getDeclaredMethod("classFromType", Type.class);
        classFromType.setAccessible(true);
        Method rawClass = SearchPath.class.getDeclaredMethod("rawClass", Type.class);
        rawClass.setAccessible(true);

        assertEquals(String.class, collectionElementType.invoke(null, Object.class, genericArray(String.class)));
        assertNull(collectionElementType.invoke(null, String.class, String.class));
        assertEquals(Line.class, genericArgumentFromType.invoke(null, parameterized(LineBag.class), Iterable.class, 0));
        assertNull(genericArgumentFromType.invoke(null, parameterized(new UnknownType()), Iterable.class, 0));
        assertNull(genericArgumentFromType.invoke(null, parameterized(String.class), Iterable.class, 0));
        assertNull(genericArgumentFromClass.invoke(null, null, Iterable.class, 0));
        assertNull(genericArgumentFromClass.invoke(null, Object.class, Iterable.class, 0));
        assertEquals(String[].class, classFromType.invoke(null, genericArray(String.class)));
        assertNull(classFromType.invoke(null, wildcardWithoutUpperBounds()));
        assertNull(classFromType.invoke(null, GenericRoot.class.getTypeParameters()[0]));
        assertNull(classFromType.invoke(null, new UnknownType()));
        assertEquals(List.class, rawClass.invoke(null, parameterized(List.class, String.class)));
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

    private static GenericArrayType genericArray(Type componentType) {
        return () -> componentType;
    }

    private static ParameterizedType parameterized(Class<?> rawType, Type... arguments) {
        return parameterized((Type) rawType, arguments);
    }

    private static ParameterizedType parameterized(Type rawType, Type... arguments) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return arguments;
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    private static WildcardType wildcardWithoutUpperBounds() {
        return new WildcardType() {
            @Override
            public Type[] getUpperBounds() {
                return new Type[0];
            }

            @Override
            public Type[] getLowerBounds() {
                return new Type[0];
            }
        };
    }

    private static final class UnknownType implements Type {
    }

    private static final class FieldOnlyRoot {
        @SuppressWarnings("unused")
        private String code;
    }

    private static class BaseFieldRoot {
        @SuppressWarnings("unused")
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

    private static final class IndexedOnlyRoot {
        private String[] values = new String[0];

        public String getValues(int index) {
            return values[index];
        }

        public void setValues(int index, String value) {
            values[index] = value;
        }
    }

    private static final class IndexedOnlyNoFieldRoot {
        public String getValues(int index) {
            return "value-" + index;
        }

        public void setValues(int index, String value) {
            if (index < 0) {
                throw new IllegalArgumentException(value);
            }
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

        public List<Line[]> getLineArrays() {
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

        @ElementCollection
        private String scalarLabel;

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

        public String getScalarLabel() {
            return scalarLabel;
        }
    }
}
