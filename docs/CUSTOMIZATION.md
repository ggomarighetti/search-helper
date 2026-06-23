# Customization

The everyday API is intentionally small, but the RSQL layer remains extensible.

| Extension point | Use case |
|---|---|
| `SearchRsqlEngineCustomizer` | Customize the auto-configured RSQL engine |
| `RsqlOperatorDescriptor` | Register parser symbols, arity, conversion type, and custom JPA execution |
| `PerplexhubRsqlBackendOptions` | Tune the bundled Perplexhub adapter |
| `RsqlBackendAdapter` | Replace the Perplexhub-backed compiler |
| `RsqlParserFactory` | Replace parser construction |
| `SearchDefinitionValidator` | Add runtime definition checks |
| `ConversionService` / `Converter<String, T>` | Add application-specific value conversion |
| `rsql.jpa.search.rsql.perplexhub.*` | Configure the bundled Perplexhub backend |

`SearchRsqlEngine.builder()` starts with the built-in operator dialect. For a
strictly custom dialect, call `.withoutDefaultOperators()` before adding custom
`RsqlOperatorDescriptor` instances. In Spring Boot, a `SearchRsqlEngineCustomizer`
can do the same for the auto-configured engine.

## Perplexhub Backend

The default backend is `PerplexhubRsqlBackendAdapter`. It wraps Perplexhub's
`RSQLJPAPredicateConverter`, but it does not expose entity paths directly to the
client. The adapter receives the validated `SearchDefinition`, passes public
selector-to-JPA-path aliases to Perplexhub, applies the configured
`ConversionService`, forwards the backend options, and turns any descriptor with
`.jpaPredicate(...)` into a Perplexhub `RSQLCustomPredicate`.

That means there are two levels of operator customization:

- built-in operators such as `==`, `=in=`, `=ilike=`, and `=between=` are
  registered by the library and executed through Perplexhub's native support;
- application operators are registered with `RsqlOperatorDescriptor` and must
  provide `.jpaPredicate(...)` so the Perplexhub adapter knows how to build the
  JPA `Predicate`.

For a custom operator executed by the default backend, always declare:

- a logical `RsqlOperator`;
- one or more parser symbols;
- an arity;
- an `argumentType(...)` compatible with `Comparable`;
- a `jpaPredicate(...)` implementation.

`RsqlOperatorDescriptor.argumentType(...)` accepts any Java type so custom
backends can use application-specific value objects. The bundled Perplexhub
backend is stricter because `RSQLCustomPredicate` requires a `Comparable`
argument type; non-comparable argument types are supported only with a backend
that can execute them.

The predicate receives an `RsqlJpaPredicateContext` with the
`CriteriaBuilder`, resolved JPA `Path`, metamodel `Attribute`, converted
arguments, root/from, and logical operator. The same `ConversionService` is used
for library validation and Perplexhub execution, so conversion rules stay
consistent.

Backend options can be set with Spring properties:

```yaml
rsql:
  jpa:
    search:
      rsql:
        perplexhub:
          strict-equality: true
          like-escape-character: "!"
```

or by providing a bean:

```java
@Bean
PerplexhubRsqlBackendOptions perplexhubOptions() {
    return PerplexhubRsqlBackendOptions.builder()
            .strictEquality(true)
            .likeEscapeCharacter('!')
            .build();
}
```

If you need Perplexhub behavior that is not exposed by these options, replace
the `RsqlBackendAdapter` bean and compile `RsqlCompilationRequest<T>` yourself.

## Custom Operators

Create a logical `RsqlOperator`, describe its parser symbol and arity with
`RsqlOperatorDescriptor`, and register it through a Spring
`SearchRsqlEngineCustomizer` bean.

```java
@Configuration
class SearchOperatorsConfiguration {

    static final RsqlOperator STARTS_WITH = RsqlOperator.of("STARTS_WITH");

    @Bean
    SearchRsqlEngineCustomizer startsWithOperator() {
        return builder -> builder.operator(
                RsqlOperatorDescriptor.builder(STARTS_WITH)
                        .symbol("=startsWith=")
                        .arity(RsqlOperatorArity.exact(1))
                        .argumentType(String.class)
                        .jpaPredicate(context -> context.criteriaBuilder().like(
                                context.path().as(String.class),
                                context.argument(0) + "%"))
                        .build());
    }
}
```

Then allow the operator on specific fields:

```java
SearchDefinition<CatalogTextEntry> definition = SearchDefinition.builder()
        .entity(CatalogTextEntry.class)
        .fields(fields -> fields.add("code", String.class)
                .filterable(filter -> filter.allow(STARTS_WITH)))
        .paging()
        .build();
```

Custom operators are validated like built-in operators. With the default
Perplexhub backend, a registered operator that is not built in must have a JPA
predicate; otherwise the definition fails with
`RSQL_OPERATOR_NOT_EXECUTABLE`. If `.jpaPredicate(...)` is present,
`.argumentType(...)` is required so the backend can create the matching
Perplexhub custom predicate, and that type must implement `Comparable`.

## Custom Conversion

The engine uses one `ConversionService` for validation and backend compilation.
If your application exposes a unique `ConversionService` bean, auto-configuration
uses it. Otherwise, it builds an `ApplicationConversionService` and adds
application `Converter` beans.

```java
record Sku(String value) {
}

@Component
class SkuConverter implements Converter<String, Sku> {
    @Override
    public Sku convert(String source) {
        if (!source.startsWith("SKU")) {
            throw new IllegalArgumentException("SKU must start with SKU");
        }
        return new Sku(source);
    }
}
```

```java
SearchDefinition<CatalogItem> definition = SearchDefinition.builder()
        .entity(CatalogItem.class)
        .fields(fields -> fields.add("sku", Sku.class)
                .filterable(filter -> filter.allow(EQUAL)))
        .paging()
        .build();
```

Invalid converted values become `RSQL_ARGUMENT_CONVERSION_FAILED` validation
errors rather than persistence exceptions.

## Backend, Parser, and Definition Validation

Register a `RsqlBackendAdapter` bean to replace the default Perplexhub-backed
compiler, or a `RsqlParserFactory` through `SearchRsqlEngineCustomizer` to
replace parser construction. Register one or more `SearchDefinitionValidator`
beans to enforce additional runtime checks on completed definitions.
