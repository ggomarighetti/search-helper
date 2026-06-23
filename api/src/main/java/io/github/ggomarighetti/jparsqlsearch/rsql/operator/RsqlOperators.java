package io.github.ggomarighetti.jparsqlsearch.rsql.operator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;

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

    private static final Set<RsqlOperator> BOOLEAN = ordered(EQUAL, NOT_EQUAL);
    private static final Set<RsqlOperator> EXACT =
            ordered(EQUAL, NOT_EQUAL, IN, NOT_IN);
    private static final Set<RsqlOperator> ORDERED = ordered(
            EQUAL,
            NOT_EQUAL,
            IN,
            NOT_IN,
            GREATER_THAN,
            GREATER_THAN_OR_EQUAL,
            LESS_THAN,
            LESS_THAN_OR_EQUAL,
            BETWEEN,
            NOT_BETWEEN);
    private static final Set<RsqlOperator> TEXT = ordered(
            EQUAL,
            NOT_EQUAL,
            IN,
            NOT_IN,
            LIKE,
            NOT_LIKE,
            IGNORE_CASE,
            IGNORE_CASE_LIKE,
            IGNORE_CASE_NOT_LIKE);

    private static final Set<Class<?>> EXACT_TYPES = Set.of(
            UUID.class,
            URI.class,
            URL.class,
            Locale.class,
            Currency.class,
            Charset.class,
            ZoneId.class,
            InetAddress.class,
            Period.class,
            MimeType.class);
    private static final Set<Class<?>> ORDERED_TYPES = Set.of(
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            BigInteger.class,
            BigDecimal.class,
            Instant.class,
            LocalDate.class,
            LocalDateTime.class,
            LocalTime.class,
            OffsetDateTime.class,
            OffsetTime.class,
            ZonedDateTime.class,
            Year.class,
            YearMonth.class,
            MonthDay.class,
            ZoneOffset.class,
            Duration.class,
            DataSize.class);

    private RsqlOperators() {
    }

    /**
     * Returns the restrictive default operator profile for a value type.
     *
     * @param type declared field type
     * @return immutable ordered operator set, or an empty set when unsupported
     */
    public static Set<RsqlOperator> defaultsFor(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        Class<?> resolved = ClassUtils.resolvePrimitiveIfNecessary(type);
        if (CharSequence.class.isAssignableFrom(resolved)) {
            return TEXT;
        }
        if (Boolean.class.equals(resolved)) {
            return BOOLEAN;
        }
        if (Character.class.equals(resolved)
                || resolved.isEnum()
                || EXACT_TYPES.stream().anyMatch(candidate -> candidate.isAssignableFrom(resolved))) {
            return EXACT;
        }
        if (ORDERED_TYPES.stream().anyMatch(candidate -> candidate.isAssignableFrom(resolved))) {
            return ORDERED;
        }
        return Set.of();
    }

    /**
     * Reports whether a default profile exists for a value type.
     *
     * @param type declared field type
     * @return {@code true} when {@link #defaultsFor(Class)} is non-empty
     */
    public static boolean supportsDefaults(Class<?> type) {
        return !defaultsFor(type).isEmpty();
    }

    private static Set<RsqlOperator> ordered(RsqlOperator... operators) {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(java.util.List.of(operators)));
    }
}
