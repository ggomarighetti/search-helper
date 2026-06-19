package io.github.ggomarighetti.jparsqlsearch.protection;

import java.util.Objects;

/** Indicates that a request exceeded a configured search protection limit. */
public final class SearchProtectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Error code used for every protection-limit violation. */
    public static final String PROTECTION_RULE_EXCEEDED = "SEARCH_PROTECTION_RULE_EXCEEDED";

    /** Stable machine-readable error code. */
    private final String code;
    /** Name of the exceeded protection rule. */
    private final String rule;
    /** Observed value that exceeded the rule. */
    private final long actual;
    /** Configured maximum value. */
    private final long limit;

    /**
     * Creates a protection exception.
     *
     * @param rule stable name of the exceeded rule
     * @param actual observed value
     * @param limit configured limit
     */
    public SearchProtectionException(String rule, long actual, long limit) {
        super("Search request exceeds protection rule '%s' (actual: %d, limit: %d)."
                .formatted(
                        Objects.requireNonNull(rule, "rule must not be null"),
                        actual,
                        limit));
        this.code = PROTECTION_RULE_EXCEEDED;
        this.rule = rule;
        this.actual = actual;
        this.limit = limit;
    }

    /**
     * Returns the stable error code.
     *
     * @return {@link #PROTECTION_RULE_EXCEEDED}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the exceeded rule name.
     *
     * @return rule name
     */
    public String rule() {
        return rule;
    }

    /**
     * Returns the observed value.
     *
     * @return observed value
     */
    public long actual() {
        return actual;
    }

    /**
     * Returns the configured limit.
     *
     * @return configured limit
     */
    public long limit() {
        return limit;
    }
}
