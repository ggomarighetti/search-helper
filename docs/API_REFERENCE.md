# API Reference

## Main Types

| Type | Purpose |
|---|---|
| `SearchDefinition<T>` | Immutable contract for one entity and search use case |
| `SearchDefinition.Factory` | Creates definitions with application-wide path limits |
| `SearchCompiler` | Validates and compiles a complete request |
| `CompiledSearch<T>` | Resulting `Specification<T>` and validated `Pageable` |
| `SearchPolicy` | Global and definition-local protection limits |
| `RsqlOperators` | Logical identifiers for built-in operators |

## SearchDefinition Builder

Start every definition by selecting the JPA root entity:

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .build();
```

In Spring Boot applications, prefer the auto-configured
`SearchDefinition.Factory` when definitions should inherit global path-depth
policy during construction:

```java
SearchDefinition<Product> definition = searchDefinitionFactory.builder()
        .entity(Product.class)
        .fields(fields -> fields.add("country", String.class)
                .path("supplier.address.countryCode")
                .filterable())
        .paging()
        .build();
```

Definitions are designed to be reused. Prefer static fields, singleton beans, or
other long-lived holders instead of rebuilding the same definition for every
request. Definitions that are built dynamically and then discarded implement
`AutoCloseable`; call `close()` after the last use to release Hibernate
Validator factories deterministically. Long-lived definitions can simply stay
open for the lifetime of the application.

Declare public fields inside `.fields(...)`. A field starts as metadata only;
filtering and sorting are disabled until enabled explicitly.

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .fields(fields -> {
            fields.add("name", String.class);       // exposed metadata only
            fields.add("sku", String.class).filterable();
            fields.add("price", BigDecimal.class).sortable();
            fields.add("category", String.class).searchable();
        })
        .build();
```

`fields.add(selector, type)` declares the stable public selector and the value
type used for conversion and validation. The selector is also the default entity
path. Use `.path(...)` to map a public selector to a different JPA path.

```java
fields.add("customerName", String.class)
        .path("customer.name")
        .filterable()
        .sortable();
```

Filtering and sorting can use separate paths when the read model needs it:

```java
fields.add("customer", String.class)
        .filterable(filter -> filter
                .path("customer.name")
                .allow(EQUAL, IGNORE_CASE_LIKE))
        .sortable(sort -> sort
                .path("customer.sortName")
                .allow(ASC));
```

Subtype-only fields are supported through JPA `treat` by declaring
`.subtype(...)`:

```java
fields.add("birthDate", LocalDate.class)
        .subtype(NaturalPerson.class)
        .filterable()
        .sortable();
```

Definition paths are checked against Java properties while the DSL is built and,
in JPA applications, against the JPA metamodel when first compiled. Collection
element types can be resolved from concrete generic supertypes and bounded type
variables, such as `List<T extends Line>`.

## Filtering

`.filterable()` enables the restrictive default operator profile for the field
type. `.filterable(filter -> ...)` starts with an empty whitelist unless
`filter.withDefaults()` is called.

```java
fields.add("price", BigDecimal.class)
        .filterable(filter -> filter
                .withDefaults()
                .deny(IN)
                .allow(IS_NULL));
```

The filtering DSL supports:

| Method | Effect |
|---|---|
| `filter.path(...)` | Overrides the filtering JPA path |
| `filter.withDefaults()` | Adds the type-aware default operator profile |
| `filter.allow(operator...)` | Allows one or more operators without extra rules |
| `filter.allow(operator, rules -> ...)` | Allows an operator and adds validation |
| `filter.allow(operator, argumentType, rules -> ...)` | Uses an explicit conversion/validation type |
| `filter.deny(operator...)` | Removes operators from the effective whitelist |

Default operator profiles are type-aware:

| Field type | Default profile |
|---|---|
| Text | equality, lists, LIKE, and case-insensitive operators |
| Boolean | equality and inequality |
| Enum, UUID, and exact scalar types | equality and lists |
| Numbers and temporal types | equality, lists, ordering, and ranges |

Null operators are never included by default. Opt into `IS_NULL` or `NOT_NULL`
only where nullability is part of the public API.

Built-in logical operators are available through `RsqlOperators`:

```java
EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
LESS_THAN, LESS_THAN_OR_EQUAL, IN, NOT_IN,
IS_NULL, NOT_NULL, LIKE, NOT_LIKE, IGNORE_CASE,
IGNORE_CASE_LIKE, IGNORE_CASE_NOT_LIKE, BETWEEN, NOT_BETWEEN
```

Operator declarations can validate each converted argument with `each(...)` and
the complete converted argument list with `args(...)`. Rules are declared with
Hibernate Validator's programmatic constraint definitions.

```java
fields.add("taxId", String.class)
        .filterable(filter -> filter
                .allow(EQUAL, operator -> operator
                        .each(each -> each
                                .rule(new SizeDef().min(11).max(11))
                                .rule(new PatternDef().regexp("\\d+"))))
                .allow(IN, operator -> operator
                        .args(args -> args.rule(new SizeDef().max(20)))
                        .each(each -> each
                                .rule(new SizeDef().min(11).max(11))
                                .rule(new PatternDef().regexp("\\d+")))));
```

Values are converted before validation. Invalid UUIDs, enums, numbers, dates,
or custom converted values become structured RSQL validation errors instead of
persistence failures.

Collection-valued filter paths are detected automatically:

```java
fields.add("reviewRating", Integer.class)
        .path("reviews.rating")
        .filterable(filter -> filter.allow(GREATER_THAN_OR_EQUAL));
```

When a to-many selector appears in the filter, the generated query uses
`distinct(true)` to prevent duplicate root rows. Sorting through collection
paths is rejected.

## Sorting

`.sortable()` enables both `ASC` and `DESC` directions on the effective path.
`.sortable(sort -> ...)` customizes the sort contract.

```java
fields.add("name", String.class)
        .sortable(sort -> sort
                .allow(ASC)
                .allowIgnoreCase()
                .allowNullHandling(NULLS_LAST));
```

The sorting DSL supports:

| Method | Effect |
|---|---|
| `sort.path(...)` | Overrides the sorting JPA path |
| `sort.allow(...)` | Restricts accepted `Sort.Direction` values |
| `sort.allowIgnoreCase()` | Allows Spring Data case-insensitive sort orders |
| `sort.allowNullHandling(...)` | Allows explicit Spring Data null handling modes |

Global sorting policy can still reject relation sorting, too many orders,
case-insensitive orders, explicit null handling, or to-many paths.

## Searchable Fields

`.searchable()` is a convenience method that enables default filtering and both
sort directions:

```java
fields.add("name", String.class).searchable();
```

It is equivalent to:

```java
fields.add("name", String.class)
        .filterable()
        .sortable();
```

## Query Text

RSQL filtering and the optional `query` parameter are separate concerns. The
library validates query text and delegates persistence semantics to your own
`Specification<T>` factory.

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .query(query -> query
                .rule(new SizeDef().min(3).max(80))
                .specification(ProductSpecifications::matchesTerm))
        .paging()
        .build();
```

Your application can implement query matching with database functions,
normalized columns, indexed expressions, or ordinary Criteria API predicates.
`rsql-jpa-search` does not force a search strategy.

## Paging

`.paging()` enables paging with definition-level default rules. `.paging(...)`
adds Hibernate Validator rules for page number and page size.

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .paging(paging -> paging
                .page(page -> page.rule(new MinDef().value(0)))
                .size(size -> size.rule(new MaxDef().value(50))))
        .build();
```

Definition rules are applied in addition to global paging policy.

## Local Policy Overrides

`.limits(...)` customizes the protection policy for one definition. Customizer
based limits overlay only the values explicitly changed on top of the compiler's
global policy.

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .limits(limits -> limits
                .filter(filter -> filter
                        .maxComparisons(8)
                        .maxInValues(10))
                .paging(paging -> paging
                        .maxSize(25)))
        .paging()
        .build();
```

Passing a complete `SearchPolicy` to `.limits(SearchPolicy)` replaces the global
policy for that definition.

## Runtime Compilation

Use `SearchCompiler` for complete request compilation. It coordinates filtering,
query text, paging, sorting, and cross-component protection rules.

```java
CompiledSearch<Product> search = searchCompiler.compile(
        filter,
        query,
        pageable,
        PRODUCT_DEFINITION);

return productRepository.findAll(search.specification(), search.pageable());
```

Repositories must extend `JpaSpecificationExecutor<T>` to execute the compiled
`Specification<T>`.

For count-free flows, use `compileSlice(...)`. It applies slice-specific
protection instead of page count-query restrictions.

```java
CompiledSearch<Product> search = searchCompiler.compileSlice(
        filter,
        query,
        pageable,
        PRODUCT_DEFINITION);

return productSliceQuery.fetchSlice(search.specification(), search.pageable());
```

The execution method is application-owned; use a repository/query path that
actually returns a slice without issuing a count query.

Tenant isolation, authorization, visibility, and other mandatory predicates
should remain application-owned specifications. Extra specifications passed to
`compile(...)` or `compileSlice(...)` are combined with the validated RSQL and
query specifications using logical `AND`.

```java
CompiledSearch<Product> search = searchCompiler.compile(
        filter,
        query,
        pageable,
        PRODUCT_DEFINITION,
        belongsToTenant(tenantId),
        visibleTo(currentUser),
        notDeleted());
```

Clients cannot remove or override those mandatory predicates.
