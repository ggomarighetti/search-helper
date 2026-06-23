# Configuration

Spring Boot auto-configuration binds global limits from the
`rsql.jpa.search` prefix and turns them into a `SearchPolicy`. Most
applications only override the few limits that should be tighter for their data
model or traffic profile:

```yaml
rsql:
  jpa:
    search:
      filter:
        max-comparisons: 16
        max-in-values: 25
      paging:
        max-size: 50
        max-offset: 2500
      query:
        max-length: 100
```

The generated JAR includes Spring Boot configuration metadata for the complete
property set. At a high level, the policy groups are:

| Group | Covers |
|---|---|
| `rsql` | Parser enablement, raw filter length, parenthesis depth, AST size and depth |
| `rsql.perplexhub` | Options for the bundled Perplexhub-backed JPA compiler |
| `filter` | Comparison counts, arguments, `IN`, `NOT IN`, ranges, negation, OR complexity, joins, and to-many filtering |
| `filter.like` | LIKE pattern length, literal length, wildcard placement/count, and case-insensitive LIKE support |
| `paging` | Page number, size, offset, unpaged requests, and page/slice topology |
| `sorting` | Sort order count, relation sorting, case handling, null handling, joins, and to-many rejection |
| `query` | Query text length and risky combinations with to-many filtering, relation sorting, or unpaged requests |
| `paths` | Maximum dotted path depth used while building definitions |

The built-in profile is intentionally bounded: RSQL is enabled with a
4096-character maximum, AST depth is capped at 8 with at most 48 nodes, filters
allow up to 24 comparisons and 50 `IN` values, page size defaults to a maximum
of 100 with a 5000-row offset cap, unpaged requests are disabled, sort orders
are capped at 3, to-many sorting is rejected, slice compilation is enabled, and
definition paths are capped at 3 segments.

When unpaged requests are enabled with `rsql.jpa.search.paging.allow-unpaged`
or a per-definition limit override, the compiler still does not return an
unbounded `Pageable`. It records the original unpaged input for protection
checks, translates allowed sort aliases, and returns `PageRequest.of(0,
defaultUnpagedSize, translatedSort)`. Use
`rsql.jpa.search.paging.default-unpaged-size` to choose that bounded page size.

Setting `rsql.jpa.search.rsql.enabled=false` disables the built-in RSQL engine,
Perplexhub backend, and related RSQL infrastructure. In that mode the
auto-configuration still creates `SearchDefinition.Factory`, but it creates
`SearchCompiler` only when the application provides its own `SearchRsqlEngine`
bean.

Per-use case `.limits(...)` overlays remain useful when one endpoint needs a
tighter profile than the global defaults:

```java
SearchDefinition<Product> definition = SearchDefinition.builder()
        .entity(Product.class)
        .limits(limits -> limits
                .filter(filter -> filter.maxComparisons(8))
                .paging(paging -> paging.maxSize(25)))
        .paging()
        .build();
```
