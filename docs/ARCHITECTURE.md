# Architecture

`jpa-rsql-search` turns explicitly allowed search input into bounded Spring Data
JPA specifications.

## Modules

- `jpa-rsql-search-api`: public definitions, policies, paths, filtering,
  sorting, paging, and query contracts.
- `jpa-rsql-search-rsql-spi`: backend-neutral RSQL AST, parser contracts,
  operator metadata, and JPA predicate bindings.
- `jpa-rsql-search-core`: guarded compilation, validation, engine construction,
  and protection limits.
- `jpa-rsql-search-jpa-validation`: JPA metamodel validation.
- `jpa-rsql-search-perplexhub`: Perplexhub parser/backend integration.
- `jpa-rsql-search-spring-boot-starter`: auto-configuration and configuration
  metadata.
- `integration`: architecture, consumer, and PostgreSQL verification; it
  is never published.

The intended dependency direction is:

```text
api <- rsql-spi <- core <- jpa-validation
                    ^
                    |
                perplexhub
                    ^
                    |
            spring-boot-starter
```

## Runtime flow

1. The application defines an allowlist with `SearchDefinition`.
2. Definition validators confirm paths, operators, topology, and types.
3. The parser creates a bounded RSQL AST.
4. Guards enforce size, depth, value, path, join, and complexity limits.
5. A backend compiles the validated AST into a JPA `Specification`.
6. Application-owned mandatory predicates are combined with logical `AND`.
7. Paging, sorting, and query text are validated before execution.

## Trust boundaries

HTTP input, RSQL expressions, query text, sorting, and paging are untrusted.
Application definitions and mandatory predicates are trusted policy. Backend
implementations and custom operators are privileged extension points and must
be registered explicitly.

The library does not authenticate users, authorize business actions, own a
database connection, or execute HTTP requests. The consuming application owns
those boundaries.

Architecture rules and jar-boundary integration tests enforce module
separation. SonarQube Cloud also tracks the intended architecture model.
