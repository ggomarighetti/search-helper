# rsql-jpa-search

[![Verify](https://github.com/ggomarighetti/rsql-jpa-search/actions/workflows/verify.yml/badge.svg)](https://github.com/ggomarighetti/rsql-jpa-search/actions/workflows/verify.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ggomarighetti/rsql-jpa-search-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ggomarighetti/rsql-jpa-search-spring-boot-starter)
[![Javadocs](https://javadoc.io/badge2/io.github.ggomarighetti/rsql-jpa-search-api/javadoc.svg)](https://javadoc.io/doc/io.github.ggomarighetti/rsql-jpa-search-api)
[![GitHub Release](https://img.shields.io/github/v/release/ggomarighetti/rsql-jpa-search?display_name=tag)](https://github.com/ggomarighetti/rsql-jpa-search/releases)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/ggomarighetti/rsql-jpa-search/badge)](https://scorecard.dev/viewer/?uri=github.com/ggomarighetti/rsql-jpa-search)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/13353/badge)](https://www.bestpractices.dev/projects/13353)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-007396)](https://adoptium.net/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Status: stable](https://img.shields.io/badge/status-stable-brightgreen)

`rsql-jpa-search` is a small contract layer for Spring applications that accept
dynamic search input and need to turn it into Spring Data JPA artifacts. It does
not run the query itself. Your application declares a `SearchDefinition<T>` for
one search use case, passes the incoming RSQL filter, optional query text,
and `Pageable` to `SearchCompiler`, and receives a validated
`CompiledSearch<T>` containing a `Specification<T>` and a safe `Pageable`.

The library is intentionally an orchestration layer around established building
blocks instead of a replacement for them. Hibernate Validator evaluates the
application rules declared on fields, operators, query text, and paging;
[nstdio/rsql-parser](https://github.com/nstdio/rsql-parser) parses the RSQL
expression; and
[perplexhub/rsql-jpa-specification](https://github.com/perplexhub/rsql-jpa-specification)
does the default RSQL-to-JPA `Specification` translation. `rsql-jpa-search`
adds the contract that ties those pieces together: public aliases, allowlists,
conversion, limits, mandatory predicates, and structured validation errors.

```mermaid
flowchart LR
    input["filter: String<br/>query: String?<br/>pageable: Pageable"]
    repository["repository.findAll(...)<br/><small>extends JpaSpecificationExecutor&lt;T&gt;</small>"]

    subgraph helper["rsql-jpa-search"]
        direction LR
        contract["SearchCompiler<br/><small>requires SearchDefinition&lt;T&gt;</small>"]
        compiled["CompiledSearch&lt;T&gt;<br/><small>returns Specification&lt;T&gt; + Pageable</small>"]

        contract --> compiled
    end

    input --> contract
    compiled --> repository
```

The definition is the application-owned boundary: public field names, entity
paths, allowed operators, value conversion and validation rules, paging,
sorting, optional query behavior, mandatory predicates, and protection
limits live in one declared model. Each request is validated against that model
before a repository sees the generated `Specification` and `Pageable`.

That lets a search API grow from a handful of filters to richer query use cases
without adding endpoint-specific parameters for every field, join, operator, or
sort order. Clients keep the flexible shape that makes RSQL useful, while the
server keeps a narrow and explicit contract around what can actually be queried.

## What is RSQL?

RSQL is a compact URL filter syntax for expressing comparisons and boolean
logic. It is useful when an endpoint needs dynamic filtering but should not grow
a new query parameter for every possible field/operator pair. A single query
string can describe nested `AND` / `OR` expressions:

```mermaid
flowchart LR
    subgraph queryBox["RSQL Query"]
        direction TB
        query["'(name=ilike=phone,sku==ABC);status==ACTIVE'"]
    end

    subgraph treeBox["Logical AST"]
        direction LR
        root["AND"]
        either["OR"]
        nameMatch["name ILIKE phone"]
        skuMatch["sku EQUAL ABC"]
        statusMatch["status EQUAL ACTIVE"]

        root --> either
        either --> nameMatch
        either --> skuMatch
        root --> statusMatch
    end

    queryBox -- means --> treeBox
```

In RSQL, `;` means logical `AND`, `,` means logical `OR`, selectors identify
public fields, and comparison operators such as `==`, `=in=`, `=ge=`, or
`=ilike=` describe the filter operation.

RSQL itself only describes the expression. It does not decide whether `name`,
`sku`, `price`, or `status` are entity attributes, joined paths, computed
fields, or stable API aliases. `rsql-jpa-search` keeps that mapping in
`SearchDefinition`, validates that each selector/operator/value combination is
allowed, and then compiles the accepted expression into a JPA `Specification`.

This project uses
[nstdio/rsql-parser](https://github.com/nstdio/rsql-parser), a maintained fork
of the original [jirutka/rsql-parser](https://github.com/jirutka/rsql-parser).

## Why a Search Contract?

Direct RSQL-to-JPA translation is useful, and this library uses it under the
hood. It is a good fit when an endpoint has a small trusted surface or when the
application already owns a complete search contract before the repository sees
the generated `Specification`.

For public APIs, the direct path usually needs an explicit boundary in front of
JPA. Public field names should not have to be entity paths, each field needs an
operator and sort policy, values should be converted and validated before
persistence sees them, paging and sorting need bounded limits, and
application-owned predicates such as tenant, authorization, visibility, or
soft-delete filters must not be removable by clients.

`rsql-jpa-search` adds that contract while still relying on established RSQL
building blocks underneath it. `nstdio/rsql-parser` handles the syntax,
[perplexhub/rsql-jpa-specification](https://github.com/perplexhub/rsql-jpa-specification)
handles the default JPA `Specification` translation, Hibernate Validator
handles declared rules, and this library orchestrates those pieces into a
single validated `CompiledSearch<T>`.

## Getting Started

Most Spring Boot applications should start with the starter. It brings in the
API, core compiler, JPA validation, and default Perplexhub backend.

Add it from Maven Central with Maven:

```xml
<dependency>
  <groupId>io.github.ggomarighetti</groupId>
  <artifactId>rsql-jpa-search-spring-boot-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

Or with Gradle Kotlin DSL:

```kotlin
implementation("io.github.ggomarighetti:rsql-jpa-search-spring-boot-starter:2.0.0")
```

Applications that do not use Spring Boot can depend on the individual
`rsql-jpa-search-*` modules instead.

The runtime flow is a regular Spring Data repository plus an application-owned
search definition. The repository only needs to support Spring Data JPA
specifications:

```java
public interface ProductRepository
        extends JpaRepository<Product, UUID>,
                JpaSpecificationExecutor<Product> {
}
```

Then declare the API-facing search contract. In the `execute` method below, the
incoming filter, query text, and `Pageable` are compiled into a
`CompiledSearch<Product>`; its validated `Specification` and safe `Pageable`
are passed to the repository, and the repository returns the resulting
`Page<Product>`.

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductSearchUseCase {

    private final SearchCompiler searchCompiler;
    private final ProductRepository productRepository;

    private static final SearchDefinition<Product> PRODUCT_DEFINITION =
            SearchDefinition.builder()
                    .entity(Product.class)
                    .fields(fields -> {
                        fields.add("id", UUID.class)
                                .sortable();

                        fields.add("name", String.class)
                                .searchable();

                        fields.add("category", String.class)
                                .path("category.name")
                                .filterable(filter -> filter
                                        .withDefaults()
                                        .deny(IN)
                                        .allow(IS_NULL))
                                .sortable(sort -> sort.allow(ASC));
                    })
                    .query(query -> query
                            .rule(new SizeDef().min(3).max(80))
                            .specification(ProductSpecs::queryByNameOrSku))
                    .paging(paging -> paging
                            .size(size -> size.rule(new MaxDef().value(50))))
                    .build();

    public Page<Product> execute(String filter, String query, Pageable pageable) {
        CompiledSearch<Product> search =
                searchCompiler.compile(filter, query, pageable, PRODUCT_DEFINITION);

        return productRepository.findAll(search.specification(), search.pageable());
    }
}
```

Continue with the [documentation index](docs/README.md) for the full API,
configuration, error handling, customization, architecture, and security guides.

## Security

If you think you found a vulnerability, please report it privately through
[GitHub Security Advisories](https://github.com/ggomarighetti/rsql-jpa-search/security/advisories/new)
instead of opening a public issue. For ordinary bugs, documentation problems,
or feature requests, use GitHub issues.

The supported-version policy and disclosure process are documented in
[SECURITY.md](SECURITY.md).

## Contributing

Issues, bug reports, documentation improvements, and pull requests are welcome.

The development workflow, review policy, pull request standards, Conventional
Commit rules, and required DCO sign-off are documented in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

Released under the [MIT License](LICENSE).
