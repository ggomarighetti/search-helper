package io.github.ggomarighetti.searchhelper.filter;

import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.searchhelper.validation.HibernateRuleValidator;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import java.util.Objects;
import org.hibernate.validator.cfg.ConstraintDef;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

/**
 * Validation contract for one allowed RSQL operator.
 *
 * @param <T> converted argument type
 */
public final class FilterOperator<T> {
    private final RsqlOperator operator;
    private final Class<T> argumentType;
    private final HibernateRuleValidator<ArrayList<T>> args;
    private final HibernateRuleValidator<T> each;

    private FilterOperator(
            RsqlOperator operator,
            Class<T> argumentType,
            HibernateRuleValidator<ArrayList<T>> args,
            HibernateRuleValidator<T> each) {
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.argumentType = Objects.requireNonNull(argumentType, "argumentType must not be null");
        this.args = Objects.requireNonNull(args, "args must not be null");
        this.each = Objects.requireNonNull(each, "each must not be null");
    }

    static <T> Builder<T> builder(RsqlOperator operator) {
        return new Builder<>(operator);
    }

    /**
     * Returns the conversion target.
     *
     * @return type used to convert and validate raw arguments
     */
    public Class<T> argumentType() {
        return argumentType;
    }

    /**
     * Converts and validates raw operator arguments.
     *
     * @param arguments raw RSQL arguments
     * @param conversionService conversion service shared with backend execution
     * @return detailed immutable validation result
     */
    public FilterValidationResult validate(
            List<String> arguments,
            ConversionService conversionService) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(conversionService, "conversionService must not be null");
        List<T> values = new ArrayList<>();
        List<FilterValidationError> errors = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            T converted = convert(argument, conversionService);
            if (converted == null) {
                errors.add(FilterValidationError.conversionFailed(index, argumentType));
                continue;
            }
            values.add(converted);
            for (var violation : each.violations(converted)) {
                errors.add(FilterValidationError.argumentRule(index, violation));
            }
        }
        if (values.size() == arguments.size()) {
            for (var violation : args.violations(new ArrayList<>(values))) {
                errors.add(FilterValidationError.argumentsRule(violation));
            }
        }
        return new FilterValidationResult(errors);
    }

    boolean accepts(
            List<String> arguments,
            ConversionService conversionService) {
        return validate(arguments, conversionService).accepted();
    }

    private T convert(String argument, ConversionService conversionService) {
        if (String.class.equals(argumentType)) {
            return argumentType.cast(argument);
        }
        if (!conversionService.canConvert(String.class, argumentType)) {
            return null;
        }
        try {
            return conversionService.convert(argument, argumentType);
        } catch (ConversionException exception) {
            if (isInputConversionFailure(exception)) {
                return null;
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isInputConversionFailure(ConversionException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Builds per-argument and aggregate validation rules.
     *
     * @param <T> converted argument type
     */
    public static final class Builder<T> {
        private final RsqlOperator operator;
        private final Rules<List<T>> args = new Rules<>();
        private final Rules<T> each = new Rules<>();

        private Builder(RsqlOperator operator) {
            this.operator = Objects.requireNonNull(operator, "operator must not be null");
        }

        /**
         * Adds rules evaluated against the complete converted argument list.
         *
         * @param customizer rule customizer
         * @return this builder
         */
        public Builder<T> args(Consumer<Rules<List<T>>> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            customizer.accept(args);
            return this;
        }

        /**
         * Adds rules evaluated against every converted argument.
         *
         * @param customizer rule customizer
         * @return this builder
         */
        public Builder<T> each(Consumer<Rules<T>> customizer) {
            Objects.requireNonNull(customizer, "customizer must not be null");
            customizer.accept(each);
            return this;
        }

        FilterOperator<T> build(Class<T> type) {
            return new FilterOperator<>(
                    operator,
                    type,
                    HibernateRuleValidator.forType(ArrayList.class, args.rules()),
                    HibernateRuleValidator.forType(type, each.rules()));
        }
    }

    /**
     * Mutable declaration of Hibernate Validator constraints.
     *
     * @param <T> value type validated by the rules
     */
    public static final class Rules<T> {
        private final List<ConstraintDef<?, ?>> constraints = new ArrayList<>();

        /** Creates an empty rule declaration. */
        public Rules() {
            // Intentionally empty: callers add optional constraints through rule(...).
        }

        /**
         * Adds one programmatic Hibernate Validator constraint.
         *
         * @param rule constraint definition
         * @return this declaration
         */
        public Rules<T> rule(ConstraintDef<?, ?> rule) {
            constraints.add(Objects.requireNonNull(rule, "rule must not be null"));
            return this;
        }

        private List<ConstraintDef<?, ?>> rules() {
            return List.copyOf(constraints);
        }
    }
}
