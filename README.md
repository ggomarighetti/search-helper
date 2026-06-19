# jpa-rsql-search

Guarded RSQL compilation for Spring Data JPA.

Version 2 is a direct modular redesign. It intentionally removes the 1.x
coordinate, package aliases, compatibility bridges, and static factories on
`SearchRsqlEngine`.

## Requirements

- Java 17 or newer
- Spring Boot 4.x for starter-based applications
- Spring Data JPA

## Installation

The recommended entry point is the combined Spring Boot starter:

```xml
<dependency>
  <groupId>io.github.ggomarighetti</groupId>
  <artifactId>jpa-rsql-search-spring-boot-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.ggomarighetti:jpa-rsql-search-spring-boot-starter:2.0.0")
```

The old `io.github.ggomarighetti:jpa-rsql-search` coordinate is frozen on 1.x
and will not publish a 2.x artifact or relocation POM.

## Quick start

Use a normal Spring Data repository:

```java
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                JpaSpecificationExecutor<Product> {
}
```

Declare exactly what the endpoint may expose:

```java
private static final SearchDefinition<Product> PRODUCTS =
        SearchDefinition.builder()
                .entity(Product.class)
                .fields(fields -> {
                    fields.add("id", UUID.class)
                            .sortable();

                    fields.add("name", String.class)
                            .searchable();

                    fields.add("price", BigDecimal.class)
                            .filterable(filter -> filter.withDefaults())
                            .sortable();

                    fields.add("category", String.class)
                            .path("category.name")
                            .filterable()
                            .sortable();
                })
                .paging()
                .build();
```

Inject the auto-configured compiler and execute the validated result:

```java
@Service
@Transactional(readOnly = true)
public class ProductSearchService {
    private final SearchCompiler compiler;
    private final ProductRepository products;

    public ProductSearchService(SearchCompiler compiler, ProductRepository products) {
        this.compiler = compiler;
        this.products = products;
    }

    public Page<Product> search(String filter, String query, Pageable pageable) {
        CompiledSearch<Product> search =
                compiler.compile(filter, query, pageable, PRODUCTS);
        return products.findAll(search.specification(), search.pageable());
    }
}
```

`SearchDefinition` is an allowlist, not a reflection shortcut. Only declared
selectors, paths, operators, sort directions, paging modes, and query
specifications can reach JPA.

## Runtime model

The compiler applies one bounded pipeline:

1. Validate the definition against the engine and JPA metamodel.
2. Parse and normalize RSQL.
3. Enforce operator, value, path, topology, and complexity limits.
4. Compile the backend `Specification`.
5. Combine application-owned mandatory specifications with `AND`.
6. Validate paging, sorting, query text, and whole-request protection limits.

Use `compileSlice(...)` when the endpoint does not need a count query.

## Configuration

The starter contributes:

- `SearchDefinitionFactory`
- `JpaSearchDefinitionValidator` when JPA is available
- a Perplexhub-backed `SearchRsqlEngine`
- `SearchCompiler`
- configuration metadata for `jpa.rsql.search.*`

Example:

```yaml
jpa:
  rsql:
    search:
      rsql:
        enabled: true
        max-length: 2048
        max-nodes: 64
      filter:
        max-comparisons: 16
        max-in-values: 50
        allow-to-many-filtering: false
      paging:
        max-size: 100
        allow-unpaged: false
      sorting:
        max-orders: 4
        disallow-to-many-sorting: true
      query:
        max-length: 120
      paths:
        max-depth: 4
```

Application beans replace starter defaults through Spring Boot's normal
conditional auto-configuration:

- `RsqlBackendAdapter`
- `SearchRsqlEngine`
- `ConversionService`
- `SearchDefinitionValidator`
- `SearchRsqlEngineCustomizer`

Set `jpa.rsql.search.rsql.enabled=false` when providing the engine yourself.

## Custom operators

Operator metadata is backend-neutral in v2. JPA execution is registered
separately.

```java
@Configuration
class SearchOperatorsConfiguration {
    static final RsqlOperator STARTS_WITH = RsqlOperator.of("STARTS_WITH");

    @Bean
    SearchRsqlEngineCustomizer startsWithOperator() {
        RsqlOperatorDescriptor descriptor =
                RsqlOperatorDescriptor.builder(STARTS_WITH)
                        .symbol("=startsWith=")
                        .arity(RsqlOperatorArity.exact(1))
                        .argumentType(String.class)
                        .build();

        return builder -> builder.operator(
                descriptor,
                context -> context.criteriaBuilder().like(
                        context.path().as(String.class),
                        context.argument(0) + "%"));
    }
}
```

The equivalent two-step registration is:

```java
return builder -> builder
        .operator(descriptor)
        .jpaPredicate(STARTS_WITH, predicateFactory);
```

Then allow the logical operator only on selected fields:

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .fields(fields -> fields.add("sku", String.class)
                .filterable(filter -> filter.allow(STARTS_WITH)))
        .paging()
        .build();
```

With the bundled Perplexhub backend, a custom operator must declare an argument
type, provide a JPA predicate, and use a comparable argument type.

## Explicit engine construction

All generic engine construction starts with an explicit backend:

```java
SearchRsqlEngine engine = SearchRsqlEngines.builder(myBackend)
        .conversionService(conversionService)
        .build();
```

For the bundled backend:

```java
SearchRsqlEngine defaultEngine = PerplexhubRsqlEngines.defaults();

SearchRsqlEngine customized = PerplexhubRsqlEngines.builder()
        .operator(descriptor, predicateFactory)
        .build();
```

A backend validates definitions through
`validate(RsqlBackendValidationContext context)` and compiles
`RsqlCompilationRequest<T>`.

## Modules

| Artifact | Purpose |
| --- | --- |
| `jpa-rsql-search-api` | Definitions, policy, paths, filtering, paging, sorting, query, logical operators, and definition-time validation |
| `jpa-rsql-search-rsql-spi` | RSQL AST, neutral operator metadata, backend contracts, parser contracts, and JPA operator bindings |
| `jpa-rsql-search-core` | Engine construction, guarded compilation, runtime validation errors, and protection |
| `jpa-rsql-search-jpa-validation` | JPA metamodel validation |
| `jpa-rsql-search-perplexhub` | Perplexhub backend adapter and convenience engine factory |
| `jpa-rsql-search-spring-boot-starter` | Combined dependency, auto-configuration, and configuration metadata |

Each reactor module is a first-level repository directory; there is no
additional `modules/` container.

Advanced consumers may depend on selected modules instead of the starter:

```xml
<dependency>
  <groupId>io.github.ggomarighetti</groupId>
  <artifactId>jpa-rsql-search-core</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>io.github.ggomarighetti</groupId>
  <artifactId>jpa-rsql-search-perplexhub</artifactId>
  <version>2.0.0</version>
</dependency>
```

`jpa-rsql-search-parent` is public release metadata, not the application entry
point. `integration-tests` is never published.

## Package ownership

Notable v2 imports:

```java
import io.github.ggomarighetti.jparsqlsearch.path.SearchPath;
import io.github.ggomarighetti.jparsqlsearch.definition.validation.SearchDefinitionValidator;
import io.github.ggomarighetti.jparsqlsearch.page.validation.SearchPageableValidationException;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.query.validation.SearchQueryValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngines;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaPredicateFactory;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
```

The old FQCNs do not exist in v2. See [MIGRATION_V2.md](MIGRATION_V2.md) for the
complete breaking-change inventory.

## Errors

Validation failures use structured exceptions and stable error codes:

- `RsqlFilterValidationException`
- `SearchPageableValidationException`
- `SearchQueryValidationException`
- `SearchDefinitionValidationException`
- `SearchProtectionException`

Do not expose raw persistence exceptions as client validation errors. Map the
structured codes and field paths at the application boundary.

## Verification

```bash
./mvnw verify
./mvnw -Pcoverage verify
./mvnw -Prelease verify
./mvnw -Ppublication -DskipTests deploy
```

The reactor also enforces dependency direction, package/class uniqueness across
jars, consumer compilation, strict Javadocs, and aggregate JaCoCo coverage.

## License

MIT
