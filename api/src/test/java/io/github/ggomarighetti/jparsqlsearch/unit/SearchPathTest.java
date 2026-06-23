package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
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
    void resolvesCollectionElementTypesFromGenericBoundsAndConcreteSupertypes() {
        SearchPath.Metadata bounded = SearchPath.metadata(
                GenericRoot.class,
                "sku",
                "genericLines.sku",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata concrete = SearchPath.metadata(
                ConcreteGenericRoot.class,
                "sku",
                "concreteLines.sku",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata nested = SearchPath.metadata(
                ConcreteForwardingGenericRoot.class,
                "sku",
                "concreteLines.sku",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata interfaceResolved = SearchPath.metadata(
                InterfaceGenericRoot.class,
                "sku",
                "interfaceLines.sku",
                String.class,
                DEEP_PATHS);
        SearchPath.Metadata wildcardConcrete = SearchPath.metadata(
                ConcreteWildcardGenericRoot.class,
                "sku",
                "wildcardConcreteLines.sku",
                String.class,
                DEEP_PATHS);

        assertEquals(String.class, bounded.type());
        assertTrue(bounded.traversesCollection());
        assertEquals(String.class, concrete.type());
        assertTrue(concrete.traversesCollection());
        assertEquals(String.class, nested.type());
        assertTrue(nested.traversesCollection());
        assertEquals(String.class, interfaceResolved.type());
        assertTrue(interfaceResolved.traversesCollection());
        assertEquals(String.class, wildcardConcrete.type());
        assertTrue(wildcardConcrete.traversesCollection());
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
                () -> SearchPath.metadata(GenericRoot.class, "sku", "nestedLines.sku", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "arrayLines.sku", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(GenericRoot.class, "sku", "lineArrays.sku", String.class, DEEP_PATHS));
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(RawIterableRoot.class, "sku", "rawLines.sku", String.class, DEEP_PATHS));
    }

    @Test
    void rejectsBlankMissingAndTooDeepSegments() {
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", ".name", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", "name.", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", ".", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(TestTypes.Product.class, "name", "customer..name", String.class, DEEP_PATHS));
        thrownBy(
                IllegalArgumentException.class,
                () -> SearchPath.metadata(
                        TestTypes.Product.class,
                        "name",
                        "customer.name.",
                        String.class,
                        DEEP_PATHS));
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
    void privateGenericHelpersHandleGenericArgumentFallbacks() throws ReflectiveOperationException {
        Method collectionElementType = genericResolverMethod(
                "collectionElementType",
                Class.class,
                Type.class,
                Map.class);
        Method genericArgument = genericResolverMethod(
                "genericArgument",
                Type.class,
                Class.class,
                int.class,
                Map.class);
        Method genericArgumentClass = genericResolverMethod(
                "genericArgument",
                Class.class,
                Class.class,
                int.class,
                Map.class);

        assertEquals(String.class, collectionElementType.invoke(null, Object.class, genericArray(String.class), Map.of()));
        assertNull(collectionElementType.invoke(null, String.class, String.class, Map.of()));
        assertEquals(Line.class, genericArgument.invoke(null, parameterized(LineBag.class), Iterable.class, 0, Map.of()));
        assertNull(genericArgument.invoke(null, parameterized(new UnknownType(), Line.class), Iterable.class, 0, Map.of()));
        assertEquals(Object.class, genericArgument.invoke(null, parameterized(List.class), Iterable.class, 0, Map.of()));
        assertNull(genericArgument.invoke(null, parameterized(String.class, Line.class), Iterable.class, 0, Map.of()));
        assertNull(genericArgumentClass.invoke(null, Object.class, Iterable.class, 0, Map.of()));
        assertNull(genericArgumentClass.invoke(null, String.class, Iterable.class, 0, Map.of()));
        assertNull(genericArgumentClass.invoke(null, NonResolvingInterfaceRoot.class, Iterable.class, 0, Map.of()));
    }

    @Test
    void privateGenericHelpersHandleUnresolvedReflectiveTypes() throws ReflectiveOperationException {
        Method classFromType = genericResolverMethod("classFromType", Type.class, Map.class);
        Method typeVariables = genericResolverMethod("typeVariables", Class.class, Class.class);
        Method resolveTypeVariables = genericResolverMethod("resolveTypeVariables", Type.class, Class.class, Map.class);
        Method recordTypeVariables = genericResolverMethod("recordTypeVariables", Type.class, Class.class, Map.class);

        assertNull(classFromType.invoke(null, new UnknownType(), Map.of()));
        assertNull(classFromType.invoke(null, genericArray(new UnknownType()), Map.of()));
        assertNull(classFromType.invoke(null, wildcard(new Type[0], new Type[0]), Map.of()));
        assertNull(classFromType.invoke(null, new EmptyBoundsVariable(), Map.of()));
        assertEquals(Map.of(), typeVariables.invoke(null, null, Line.class));
        assertEquals(Map.of(), typeVariables.invoke(null, Line.class, null));
        assertEquals(Map.of(), typeVariables.invoke(null, String.class, Line.class));
        recordTypeVariables.invoke(null, parameterized(GenericBase.class), GenericBase.class, new LinkedHashMap<>());
        recordTypeVariables.invoke(null, parameterized(Line.class, String.class), Line.class, new LinkedHashMap<>());

        Map<TypeVariable<?>, Type> interfaceMappings = new LinkedHashMap<>();
        assertFalse((Boolean) resolveTypeVariables.invoke(null, new UnknownType(), Object.class, interfaceMappings));
        assertTrue((Boolean) resolveTypeVariables.invoke(
                null,
                InterfaceGenericRoot.class,
                GenericLineProvider.class,
                interfaceMappings));
        assertEquals(SpecialLine.class, interfaceMappings.get(GenericLineProvider.class.getTypeParameters()[0]));
    }

    @Test
    void privateGenericHelpersSubstituteMappedReflectiveTypes() throws ReflectiveOperationException {
        Method classFromType = genericResolverMethod("classFromType", Type.class, Map.class);
        Method substituteType = genericResolverMethod("substituteType", Type.class, Map.class);
        Method rawClass = genericResolverMethod("rawClass", Type.class);
        TypeVariable<Class<GenericRoot>> variable = GenericRoot.class.getTypeParameters()[0];
        Map<TypeVariable<?>, Type> stringMapping = Map.of(variable, String.class);

        assertEquals(String[].class, classFromType.invoke(null, genericArray(variable), stringMapping));
        assertEquals(
                List[].class,
                classFromType.invoke(null, genericArray(variable), Map.of(variable, parameterized(List.class, String.class))));
        assertEquals(String.class, classFromType.invoke(null, wildcard(variable), stringMapping));

        Type substituted = (Type) substituteType.invoke(null, parameterized(List.class, variable), stringMapping);
        assertEquals(List.class, rawClass.invoke(null, substituted));
        assertNull(((ParameterizedType) substituted).getOwnerType());
        assertSame(variable, substituteType.invoke(null, variable, Map.of()));
        assertSame(variable, substituteType.invoke(null, variable, Map.of(variable, variable)));
        WildcardType unchangedWildcard = wildcard(String.class);
        assertSame(unchangedWildcard, substituteType.invoke(null, unchangedWildcard, Map.of()));
        WildcardType lowerBoundedWildcard = wildcard(new Type[] {Object.class}, new Type[] {variable});
        WildcardType substitutedWildcard = (WildcardType) substituteType.invoke(null, lowerBoundedWildcard, stringMapping);
        assertEquals(String.class, substitutedWildcard.getLowerBounds()[0]);
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

    private static Method genericResolverMethod(String name, Class<?>... parameterTypes)
            throws ReflectiveOperationException {
        Class<?> resolver = Class.forName("io.github.ggomarighetti.jparsqlsearch.path.GenericTypeResolver");
        Method method = resolver.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static GenericArrayType genericArray(Type componentType) {
        return () -> componentType;
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

    private static WildcardType wildcard(Type upperBound) {
        return wildcard(new Type[] {upperBound}, new Type[0]);
    }

    private static WildcardType wildcard(Type[] upperBounds, Type[] lowerBounds) {
        return new WildcardType() {
            @Override
            public Type[] getUpperBounds() {
                return upperBounds;
            }

            @Override
            public Type[] getLowerBounds() {
                return lowerBounds;
            }
        };
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

    private static final class RawIterableRoot {
        @SuppressWarnings("rawtypes")
        public Iterable getRawLines() {
            return List.of();
        }
    }

    private static final class UnknownType implements Type {
    }

    private static final class EmptyBoundsVariable implements TypeVariable<GenericDeclaration> {
        @Override
        public Type[] getBounds() {
            return new Type[0];
        }

        @Override
        public GenericDeclaration getGenericDeclaration() {
            return GenericRoot.class;
        }

        @Override
        public String getName() {
            return "T";
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return new AnnotatedType[0];
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }
    }

    private static class GenericBase<T extends Line> {
        public List<T> getConcreteLines() {
            return List.of();
        }
    }

    private static final class ConcreteGenericRoot extends GenericBase<SpecialLine> {
    }

    private static class ForwardingGenericRoot<T extends Line> extends GenericBase<T> {
    }

    private static final class ConcreteForwardingGenericRoot extends ForwardingGenericRoot<SpecialLine> {
    }

    private interface GenericLineProvider<T extends Line> {
        default List<T> getInterfaceLines() {
            return List.of();
        }
    }

    private static final class InterfaceGenericRoot implements GenericLineProvider<SpecialLine> {
    }

    private static class WildcardGenericBase<T extends Line> {
        public List<? extends T> getWildcardConcreteLines() {
            return List.of();
        }
    }

    private static final class ConcreteWildcardGenericRoot extends WildcardGenericBase<SpecialLine> {
    }

    private static final class SpecialLine extends Line {
    }

    private static class Line {
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

    private static final class NonResolvingInterfaceRoot implements Runnable {
        @Override
        public void run() {
            // This fixture only needs the declared Runnable interface for traversal.
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
