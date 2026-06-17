package io.github.ggomarighetti.jparsqlsearch.filter;

import io.github.ggomarighetti.jparsqlsearch.validation.RuleViolation;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Detailed failure produced while converting or validating one operator's arguments.
 *
 * @param code failure category
 * @param argumentIndex zero-based argument index, or {@code null} for list rules
 * @param targetType conversion target type name, or {@code null}
 * @param violation rule violation, or {@code null} for conversion failures
 */
public record FilterValidationError(
        Code code,
        Integer argumentIndex,
        String targetType,
        RuleViolation violation) {
    /** Validates the fields required by the selected error code. */
    public FilterValidationError {
        Objects.requireNonNull(code, "code must not be null");
        if (argumentIndex != null && argumentIndex < 0) {
            throw new IllegalArgumentException("argumentIndex must not be negative");
        }
        if (code == Code.CONVERSION_FAILED) {
            Objects.requireNonNull(targetType, "targetType must not be null for conversion failures");
        } else {
            Objects.requireNonNull(violation, "violation must not be null for rule failures");
        }
    }

    /**
     * Creates an argument conversion failure.
     *
     * @param argumentIndex zero-based argument index
     * @param targetType requested conversion type
     * @return conversion error
     */
    public static FilterValidationError conversionFailed(int argumentIndex, Class<?> targetType) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        return new FilterValidationError(
                Code.CONVERSION_FAILED,
                argumentIndex,
                targetType.getName(),
                null);
    }

    /**
     * Creates a violation for one converted argument.
     *
     * @param argumentIndex zero-based argument index
     * @param violation safe validation violation
     * @return argument-rule error
     */
    public static FilterValidationError argumentRule(int argumentIndex, RuleViolation violation) {
        return new FilterValidationError(Code.ARGUMENT_RULE, argumentIndex, null, violation);
    }

    /**
     * Creates a violation for the complete argument list.
     *
     * @param violation safe validation violation
     * @return aggregate argument-rule error
     */
    public static FilterValidationError argumentsRule(RuleViolation violation) {
        return new FilterValidationError(Code.ARGUMENTS_RULE, null, null, violation);
    }

    /**
     * Returns the optional argument position.
     *
     * @return argument index when this error identifies one argument
     */
    public OptionalInt optionalArgumentIndex() {
        return argumentIndex == null ? OptionalInt.empty() : OptionalInt.of(argumentIndex);
    }

    /**
     * Returns the optional conversion target.
     *
     * @return conversion target type when conversion failed
     */
    public Optional<String> optionalTargetType() {
        return Optional.ofNullable(targetType);
    }

    /**
     * Returns the optional rule violation.
     *
     * @return rule violation when validation failed
     */
    public Optional<RuleViolation> optionalViolation() {
        return Optional.ofNullable(violation);
    }

    /** Categories of argument conversion and validation failures. */
    public enum Code {
        /** A raw argument could not be converted. */
        CONVERSION_FAILED,
        /** A rule attached to one argument failed. */
        ARGUMENT_RULE,
        /** A rule attached to the complete argument list failed. */
        ARGUMENTS_RULE
    }
}
