package io.github.ggomarighetti.jparsqlsearch.unit;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.jpa.JpaSearchDefinitionValidator;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.Type;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;

class JpaSearchDefinitionValidatorTest {
    @Test
    void rejectsEntityMissingFromMetamodel() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> validator(Map.of()).validate(definition));

        assertEquals(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED, exception.code());
    }

    @Test
    void rejectsJpaAttributeTypeMismatch() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("email", String.class)
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        ManagedType<?> product = managedType(Map.of("email", attribute(Integer.class, false)));

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> validator(Map.of(TestTypes.Product.class, product)).validate(definition));

        assertEquals(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED, exception.code());
    }

    @Test
    void rejectsTrailingEmptyPathSegmentsAgainstMetamodel() throws Exception {
        Method resolve = JpaSearchDefinitionValidator.class.getDeclaredMethod(
                "resolve",
                Class.class,
                String.class,
                String.class,
                String.class);
        resolve.setAccessible(true);
        ManagedType<?> product = managedType(Map.of("email", attribute(String.class, false)));
        JpaSearchDefinitionValidator validator = validator(Map.of(TestTypes.Product.class, product));

        InvocationTargetException exception = thrownBy(
                InvocationTargetException.class,
                () -> resolve.invoke(validator, TestTypes.Product.class, "email", "filtering", "email."));

        assertEquals(
                SearchDefinitionValidationException.JPA_PATH_UNRESOLVED,
                ((SearchDefinitionValidationException) exception.getCause()).code());
    }

    @Test
    void rejectsCollectionAttributeThatIsNotPluralAttribute() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        ManagedType<?> product = managedType(Map.of("reviews", attribute(List.class, true)));

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> validator(Map.of(TestTypes.Product.class, product)).validate(definition));

        assertEquals(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED, exception.code());
    }

    @Test
    void rejectsNestedTypeMissingFromMetamodel() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("customerName", String.class)
                        .path("customer.name")
                        .sortable())
                .build();
        ManagedType<?> product = managedType(Map.of("customer", attribute(TestTypes.Customer.class, false)));

        SearchDefinitionValidationException exception = thrownBy(
                SearchDefinitionValidationException.class,
                () -> validator(Map.of(TestTypes.Product.class, product)).validate(definition));

        assertEquals(SearchDefinitionValidationException.JPA_PATH_UNRESOLVED, exception.code());
    }

    @Test
    void resolvesPluralAttributesThroughElementType() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("reviewRating", Integer.class)
                        .path("reviews.rating")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        ManagedType<?> product = managedType(Map.of("reviews", pluralAttribute(TestTypes.Review.class)));
        ManagedType<?> review = managedType(Map.of("rating", attribute(int.class, false)));

        validator(Map.of(TestTypes.Product.class, product, TestTypes.Review.class, review)).validate(definition);
    }

    @Test
    void resolvesTerminalPluralAttributesWithoutElementTraversal() {
        SearchDefinition<TestTypes.Product> definition = SearchDefinition.builder()
                .entity(TestTypes.Product.class)
                .fields(fields -> fields.add("tags", List.class)
                        .path("tags")
                        .filterable(filter -> filter.allow(EQUAL)))
                .build();
        ManagedType<?> product = managedType(Map.of("tags", pluralAttribute(String.class)));

        validator(Map.of(TestTypes.Product.class, product)).validate(definition);
    }

    @Test
    void rejectsSubtypeRootsOutsideTheDefinitionEntityHierarchy() throws Exception {
        Method validateSubtype =
                JpaSearchDefinitionValidator.class.getDeclaredMethod("validateSubtype", Class.class, Class.class);
        validateSubtype.setAccessible(true);
        JpaSearchDefinitionValidator validator = validator(Map.of());

        InvocationTargetException exception = thrownBy(
                InvocationTargetException.class,
                () -> validateSubtype.invoke(validator, TestTypes.Product.class, String.class));

        assertEquals(
                SearchDefinitionValidationException.JPA_PATH_UNRESOLVED,
                ((SearchDefinitionValidationException) exception.getCause()).code());
    }

    private static JpaSearchDefinitionValidator validator(Map<Class<?>, ManagedType<?>> managedTypes) {
        Metamodel metamodel = proxy(Metamodel.class, (proxy, method, args) -> {
            if (method.getName().equals("managedType")) {
                ManagedType<?> managedType = managedTypes.get(args[0]);
                if (managedType == null) {
                    throw new IllegalArgumentException("not managed");
                }
                return managedType;
            }
            throw unsupported(method);
        });
        EntityManagerFactory entityManagerFactory = proxy(EntityManagerFactory.class, (proxy, method, args) -> {
            if (method.getName().equals("getMetamodel")) {
                return metamodel;
            }
            throw unsupported(method);
        });
        return new JpaSearchDefinitionValidator(entityManagerFactory);
    }

    private static ManagedType<?> managedType(Map<String, Attribute<?, ?>> attributes) {
        return proxy(ManagedType.class, (proxy, method, args) -> {
            if (method.getName().equals("getAttribute")) {
                Attribute<?, ?> attribute = attributes.get(args[0]);
                if (attribute == null) {
                    throw new IllegalArgumentException("missing attribute");
                }
                return attribute;
            }
            throw unsupported(method);
        });
    }

    private static Attribute<?, ?> attribute(Class<?> javaType, boolean collection) {
        return proxy(Attribute.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getJavaType" -> javaType;
                case "isCollection" -> collection;
                default -> throw unsupported(method);
            };
        });
    }

    private static PluralAttribute<?, ?, ?> pluralAttribute(Class<?> elementType) {
        Type<?> type = proxy(Type.class, (proxy, method, args) -> {
            if (method.getName().equals("getJavaType")) {
                return elementType;
            }
            throw unsupported(method);
        });
        return proxy(PluralAttribute.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getJavaType" -> List.class;
                case "isCollection" -> true;
                case "getElementType" -> type;
                default -> throw unsupported(method);
            };
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass().equals(Object.class)) {
                        return objectMethod(proxy, method, args);
                    }
                    return handler.invoke(proxy, method, args);
                });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + " proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw unsupported(method);
        };
    }

    private static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException(method.toGenericString());
    }
}
