package io.github.ggomarighetti.jparsqlsearch.rsql.operator;


/** Logical identifiers for every built-in operator. */
public final class RsqlOperators {
    /** Equality. */
    public static final RsqlOperator EQUAL = RsqlOperator.of("EQUAL");
    /** Inequality. */
    public static final RsqlOperator NOT_EQUAL = RsqlOperator.of("NOT_EQUAL");
    /** Strict greater-than comparison. */
    public static final RsqlOperator GREATER_THAN = RsqlOperator.of("GREATER_THAN");
    /** Greater-than-or-equal comparison. */
    public static final RsqlOperator GREATER_THAN_OR_EQUAL = RsqlOperator.of("GREATER_THAN_OR_EQUAL");
    /** Strict less-than comparison. */
    public static final RsqlOperator LESS_THAN = RsqlOperator.of("LESS_THAN");
    /** Less-than-or-equal comparison. */
    public static final RsqlOperator LESS_THAN_OR_EQUAL = RsqlOperator.of("LESS_THAN_OR_EQUAL");
    /** Membership in a value list. */
    public static final RsqlOperator IN = RsqlOperator.of("IN");
    /** Exclusion from a value list. */
    public static final RsqlOperator NOT_IN = RsqlOperator.of("NOT_IN");
    /** Null test. */
    public static final RsqlOperator IS_NULL = RsqlOperator.of("IS_NULL");
    /** Non-null test. */
    public static final RsqlOperator NOT_NULL = RsqlOperator.of("NOT_NULL");
    /** Case-sensitive LIKE pattern. */
    public static final RsqlOperator LIKE = RsqlOperator.of("LIKE");
    /** Negated case-sensitive LIKE pattern. */
    public static final RsqlOperator NOT_LIKE = RsqlOperator.of("NOT_LIKE");
    /** Case-insensitive equality. */
    public static final RsqlOperator IGNORE_CASE = RsqlOperator.of("IGNORE_CASE");
    /** Case-insensitive LIKE pattern. */
    public static final RsqlOperator IGNORE_CASE_LIKE = RsqlOperator.of("IGNORE_CASE_LIKE");
    /** Negated case-insensitive LIKE pattern. */
    public static final RsqlOperator IGNORE_CASE_NOT_LIKE = RsqlOperator.of("IGNORE_CASE_NOT_LIKE");
    /** Inclusive range comparison. */
    public static final RsqlOperator BETWEEN = RsqlOperator.of("BETWEEN");
    /** Negated inclusive range comparison. */
    public static final RsqlOperator NOT_BETWEEN = RsqlOperator.of("NOT_BETWEEN");

    private RsqlOperators() {
    }
}
