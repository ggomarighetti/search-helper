package io.github.ggomarighetti.jparsqlsearch.filter;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
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

import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.BETWEEN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.GREATER_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IGNORE_CASE_NOT_LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LESS_THAN_OR_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.LIKE;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_BETWEEN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_EQUAL;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_IN;
import static io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators.NOT_LIKE;

/**
 * Restrictive operator profiles for common Java and Spring value types.
 *
 * <p>Null checks are deliberately excluded from every profile. Applications
 * must opt into them explicitly when their API contract needs them.
 */
public final class DefaultFilterOperators {
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

    private DefaultFilterOperators() {
    }

    /**
     * Returns the restrictive default operator profile for a value type.
     *
     * @param type declared field type
     * @return immutable ordered operator set, or an empty set when unsupported
     */
    public static Set<RsqlOperator> forType(Class<?> type) {
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
     * @return {@code true} when {@link #forType(Class)} is non-empty
     */
    public static boolean supports(Class<?> type) {
        return !forType(type).isEmpty();
    }

    private static Set<RsqlOperator> ordered(RsqlOperator... operators) {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(java.util.List.of(operators)));
    }
}
