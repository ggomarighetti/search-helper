package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.protection.SearchProtectionException;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators;
import java.util.Objects;

final class SearchTextPatternGuard {
    private final SearchPolicy.Filter.Text limits;

    SearchTextPatternGuard(SearchPolicy.Filter.Text limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    void validate(RsqlOperator operator, Iterable<String> arguments) {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        if (!isTextOperator(operator)) {
            return;
        }
        if (isIgnoreCaseOperator(operator) && !limits.allowIgnoreCase()) {
            throw exceeded("filter.text.allow-ignore-case", 1, 0);
        }
        for (String argument : arguments) {
            requireAtMost("filter.text.max-pattern-length", argument.length(), limits.maxPatternLength());
            int wildcards = countWildcards(argument);
            requireAtMost("filter.text.max-wildcards", wildcards, limits.maxWildcards());
            boolean leading = startsWithWildcard(argument);
            boolean trailing = endsWithWildcard(argument);
            if (leading && !limits.allowLeadingWildcard()) {
                throw exceeded("filter.text.allow-leading-wildcard", 1, 0);
            }
            if (trailing && !limits.allowTrailingWildcard()) {
                throw exceeded("filter.text.allow-trailing-wildcard", 1, 0);
            }
            if (leading && trailing && !limits.allowContains()) {
                throw exceeded("filter.text.allow-contains", 1, 0);
            }
            int literalLength = literalLength(argument);
            if (literalLength < limits.minLiteralLength()) {
                throw exceeded("filter.text.min-literal-length", literalLength, limits.minLiteralLength());
            }
        }
    }

    private static boolean isTextOperator(RsqlOperator operator) {
        return RsqlOperators.LIKE.equals(operator)
                || RsqlOperators.NOT_LIKE.equals(operator)
                || RsqlOperators.IGNORE_CASE.equals(operator)
                || RsqlOperators.IGNORE_CASE_LIKE.equals(operator)
                || RsqlOperators.IGNORE_CASE_NOT_LIKE.equals(operator);
    }

    private static boolean isIgnoreCaseOperator(RsqlOperator operator) {
        return RsqlOperators.IGNORE_CASE.equals(operator)
                || RsqlOperators.IGNORE_CASE_LIKE.equals(operator)
                || RsqlOperators.IGNORE_CASE_NOT_LIKE.equals(operator);
    }

    private static int countWildcards(String value) {
        int count = 0;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '*' || current == '%') {
                count++;
            }
        }
        return count;
    }

    private static boolean startsWithWildcard(String value) {
        return !value.isEmpty() && isWildcardAt(value, 0);
    }

    private static boolean endsWithWildcard(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return isWildcardAt(value, value.length() - 1);
    }

    private static int literalLength(String value) {
        int count = 0;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                count++;
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current != '*' && current != '%') {
                count++;
            }
        }
        if (escaped) {
            count++;
        }
        return count;
    }

    private static boolean isWildcardAt(String value, int index) {
        char current = value.charAt(index);
        if (current != '*' && current != '%') {
            return false;
        }
        int backslashes = 0;
        for (int cursor = index - 1; cursor >= 0 && value.charAt(cursor) == '\\'; cursor--) {
            backslashes++;
        }
        return backslashes % 2 == 0;
    }

    private static void requireAtMost(String rule, long actual, long limit) {
        if (actual > limit) {
            throw exceeded(rule, actual, limit);
        }
    }

    private static SearchProtectionException exceeded(String rule, long actual, long limit) {
        return new SearchProtectionException(rule, actual, limit);
    }
}
