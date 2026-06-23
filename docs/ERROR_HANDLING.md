# Error Handling

Request validation failures expose stable codes and safe details suitable for
API error DTOs.

| Exception | Common codes | Details |
|---|---|---|
| `RsqlFilterValidationException` | `RSQL_PARSE_ERROR`, `RSQL_RULES_FORBIDDEN`, `RSQL_LIMIT_EXCEEDED` | `errors()` returns `RsqlValidationError` values |
| `SearchPageableValidationException` | `PAGE_RULES_FORBIDDEN`, `PAGE_LIMIT_EXCEEDED`, `SORT_RULES_FORBIDDEN`, `SORT_LIMIT_EXCEEDED` | `violations()` returns `RuleViolation` values for page/size rules |
| `SearchQueryValidationException` | `QUERY_RULES_FORBIDDEN` | `violations()` returns `RuleViolation` values for query rules |
| `SearchProtectionException` | `SEARCH_PROTECTION_RULE_EXCEEDED` | `rule()`, `actual()`, and `limit()` identify the exceeded protection rule |
| `SearchDefinitionValidationException` | `PATH_LIMIT_EXCEEDED`, `JPA_PATH_UNRESOLVED`, `RSQL_CONFIGURATION_INVALID`, `RSQL_OPERATOR_NOT_REGISTERED`, `RSQL_OPERATOR_NOT_EXECUTABLE`, `RSQL_OPERATOR_TYPE_MISMATCH`, `DEFAULT_OPERATORS_UNSUPPORTED_TYPE` | Configuration failure; usually not a client `400` |

`RsqlValidationError` can identify the AST location, selector, operator,
argument index, validation path, message, message template, and constraint.
`RuleViolation` is a serializable view of a Jakarta Bean Validation violation:
it includes path, message, template, and constraint type, but intentionally omits
the invalid value.

Protection limits are intentionally allowed to win over some semantic RSQL
errors. After an operator is registered and a selector is declared, the compiler
records comparison limits before selector-specific operator checks and argument
conversion/validation. For oversized or adversarial input, callers may therefore
receive `SearchProtectionException` instead of a more specific
`RsqlValidationError` such as operator-not-allowed or argument-rule-violation.

That makes custom rules declared with `SizeDef`, `PatternDef`, `MaxDef`, and
other Hibernate Validator definitions behave like normal DTO validation from an
API boundary perspective: you can transmit structured validation details to the
client without exposing raw request values.

```java
@RestControllerAdvice
class SearchExceptionHandler {

    @ExceptionHandler(RsqlFilterValidationException.class)
    ResponseEntity<ApiError> handleRsql(RsqlFilterValidationException exception) {
        return ResponseEntity.badRequest().body(ApiError.validation(
                exception.code(),
                exception.getMessage(),
                exception.errors()));
    }

    @ExceptionHandler(SearchPageableValidationException.class)
    ResponseEntity<ApiError> handlePageable(
            SearchPageableValidationException exception) {
        return ResponseEntity.badRequest().body(ApiError.validation(
                exception.code(),
                exception.getMessage(),
                exception.violations()));
    }

    @ExceptionHandler(SearchQueryValidationException.class)
    ResponseEntity<ApiError> handleQuery(SearchQueryValidationException exception) {
        return ResponseEntity.badRequest().body(ApiError.validation(
                exception.code(),
                exception.getMessage(),
                exception.violations()));
    }

    @ExceptionHandler(SearchProtectionException.class)
    ResponseEntity<ApiError> handleProtection(SearchProtectionException exception) {
        return ResponseEntity.badRequest().body(ApiError.validation(
                exception.code(),
                exception.getMessage(),
                Map.of(
                        "rule", exception.rule(),
                        "actual", exception.actual(),
                        "limit", exception.limit())));
    }
}
```

Applications will normally map request validation and protection exceptions to
HTTP `400`. `SearchDefinitionValidationException` indicates an application
configuration problem: unresolved entity paths, invalid custom operators,
unsupported default operator profiles, or incompatible conversion/backend
contracts should fail loudly during development, startup, or first use.
