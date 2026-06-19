# Migrating from 1.x to 2.x

Version 2 is a direct breaking release. There is no facade artifact, relocation
POM, compatibility bridge, deprecated alias, or binary-compatibility layer.

## Dependency

Replace:

```xml
<artifactId>jpa-rsql-search</artifactId>
```

with:

```xml
<artifactId>jpa-rsql-search-spring-boot-starter</artifactId>
```

The 1.x coordinate remains frozen. It will never contain a 2.x release.

## Engine construction

Removed:

```java
SearchRsqlEngine.builder()
SearchRsqlEngine.defaults()
```

Use an explicit backend:

```java
SearchRsqlEngines.builder(backend)
```

or the bundled Perplexhub composition root:

```java
PerplexhubRsqlEngines.builder()
PerplexhubRsqlEngines.defaults()
```

The generic builder no longer silently chooses a backend.

## Backend validation

Replace backend validation methods that receive an engine with:

```java
void validate(RsqlBackendValidationContext context)
```

The context contains the definition, neutral operator registry, JPA binding
registry, and conversion service without coupling the backend SPI to the engine.

## Custom operators

`RsqlOperatorDescriptor` now contains only logical/parser metadata:

- logical operator
- parser symbols
- arity
- argument type

It no longer stores a JPA predicate.

Replace descriptor-owned predicates with either:

```java
builder.operator(descriptor, predicateFactory);
```

or:

```java
builder.operator(descriptor)
       .jpaPredicate(descriptor.operator(), predicateFactory);
```

The new JPA-specific contracts live under
`io.github.ggomarighetti.jparsqlsearch.rsql.jpa`.

## Package moves

| 1.x import | 2.x import |
| --- | --- |
| `definition.SearchPath` | `path.SearchPath` |
| `validation.SearchDefinitionValidator` | `definition.validation.SearchDefinitionValidator` |
| `exception.RsqlFilterValidationException` | `rsql.validation.RsqlFilterValidationException` |
| `exception.RsqlValidationError` | `rsql.validation.RsqlValidationError` |
| `exception.SearchPageableValidationException` | `page.validation.SearchPageableValidationException` |
| `exception.SearchProtectionException` | `protection.SearchProtectionException` |
| `exception.SearchQueryValidationException` | `query.validation.SearchQueryValidationException` |
| `rsql.SearchRsqlEngine` | `rsql.engine.SearchRsqlEngine` |
| `rsql.SearchRsqlEngineBuilder` | `rsql.engine.SearchRsqlEngineBuilder` |
| `rsql.SearchRsqlEngineCustomizer` | `rsql.engine.SearchRsqlEngineCustomizer` |
| `rsql.backend.RsqlJpaPredicateContext` | `rsql.jpa.RsqlJpaPredicateContext` |
| `rsql.backend.RsqlJpaPredicateFactory` | `rsql.jpa.RsqlJpaPredicateFactory` |
| `rsql.operator.DefaultRsqlOperatorDescriptors` | `rsql.metadata.DefaultRsqlOperatorDescriptors` |
| `rsql.operator.RsqlOperatorArity` | `rsql.metadata.RsqlOperatorArity` |
| `rsql.operator.RsqlOperatorDescriptor` | `rsql.metadata.RsqlOperatorDescriptor` |
| `rsql.operator.RsqlOperatorRegistry` | `rsql.metadata.RsqlOperatorRegistry` |

The old types were deleted and are not deprecated aliases.

## Maven modules

Use the starter unless you deliberately want selective dependencies.

- `jpa-rsql-search-api`
- `jpa-rsql-search-rsql-spi`
- `jpa-rsql-search-core`
- `jpa-rsql-search-jpa-validation`
- `jpa-rsql-search-perplexhub`
- `jpa-rsql-search-spring-boot-starter`

The parent is `jpa-rsql-search-parent`. The reactor's `integration-tests`
module is not public.

## Spring Boot

The starter still auto-configures the compiler, definition factory, JPA
validator, Perplexhub backend, and RSQL engine. Existing
`jpa.rsql.search.*` properties remain the configuration namespace.

Custom application beans continue to replace conditional defaults, but imports
must use the v2 packages.

## Migration checklist

1. Change the Maven or Gradle coordinate to the starter.
2. Update moved imports.
3. Replace static factories on `SearchRsqlEngine`.
4. Pass an explicit backend to generic builders.
5. Split custom operator metadata from JPA execution.
6. Update custom backend validation to use
   `RsqlBackendValidationContext`.
7. Compile and run endpoint-level search tests.
8. Recheck exception mappings for all moved runtime validation exceptions.
