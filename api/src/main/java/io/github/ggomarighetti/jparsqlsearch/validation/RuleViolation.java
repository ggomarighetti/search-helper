package io.github.ggomarighetti.jparsqlsearch.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.Serializable;
import java.lang.ref.Cleaner;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.cfg.ConstraintDef;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Serializable view of a Jakarta Bean Validation violation.
 *
 * <p>The invalid value is intentionally omitted so validation responses do not
 * accidentally expose credentials, identifiers, or other request data.
 *
 * @param path property path reported by Bean Validation
 * @param message interpolated validation message
 * @param messageTemplate original validation message template
 * @param constraint fully qualified constraint annotation type
 */
public record RuleViolation(
        String path,
        String message,
        String messageTemplate,
        String constraint) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Validates all safe violation fields. */
    public RuleViolation {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(messageTemplate, "messageTemplate must not be null");
        Objects.requireNonNull(constraint, "constraint must not be null");
    }

    /**
     * Copies this violation with a replacement path.
     *
     * @param path replacement path
     * @return copied violation
     */
    public RuleViolation withPath(String path) {
        return new RuleViolation(path, message, messageTemplate, constraint);
    }

    /**
     * Prefixes the current path.
     *
     * @param prefix path prefix
     * @return copied violation with the prefixed path
     */
    public RuleViolation prefixed(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return withPath(path.isBlank() ? prefix : prefix + "." + path);
    }

    /**
     * Executes programmatic Hibernate Validator constraints without exposing invalid values.
     *
     * @param <T> validated value type
     */
    public static final class RuleValidator<T> implements AutoCloseable {
        private static final Cleaner CLEANER = Cleaner.create();
        private static final RuleValidator<?> NONE = new RuleValidator<>(null, null);

        private final Validator validator;
        @SuppressWarnings("unused")
        private final Cleaner.Cleanable cleanable;

        private RuleValidator(Validator validator, ValidatorFactory factory) {
            this.validator = validator;
            this.cleanable = factory == null ? null : CLEANER.register(this, new ValidatorFactoryCleanup(factory));
        }

        /**
         * Returns a validator without constraints.
         *
         * @param <T> accepted value type
         * @return reusable validator that accepts every value
         */
        @SuppressWarnings("unchecked")
        public static <T> RuleValidator<T> none() {
            return (RuleValidator<T>) NONE;
        }

        /**
         * Creates a validator for programmatically declared type constraints.
         *
         * @param type runtime type receiving the constraints
         * @param rules constraint definitions
         * @param <T> validated value type
         * @return configured validator, or {@link #none()} for an empty rule list
         */
        public static <T> RuleValidator<T> forType(Class<?> type, List<ConstraintDef<?, ?>> rules) {
            if (rules.isEmpty()) {
                return none();
            }
            var configuration = Validation.byProvider(HibernateValidator.class)
                    .configure()
                    .failFast(false)
                    .messageInterpolator(new ParameterMessageInterpolator());
            ConstraintMapping mapping = configuration.createConstraintMapping();
            var context = mapping.type(type);
            rules.forEach(context::constraint);
            ValidatorFactory factory = configuration.addMapping(mapping).buildValidatorFactory();
            return new RuleValidator<>(factory.getValidator(), factory);
        }

        /**
         * Tests a value against all configured constraints.
         *
         * @param value value to validate
         * @return {@code true} when no violation is present
         */
        public boolean accepts(T value) {
            return violations(value).isEmpty();
        }

        /**
         * Returns safe, deterministically ordered violations.
         *
         * @param value value to validate
         * @return immutable violation list without the invalid value
         */
        public List<RuleViolation> violations(T value) {
            if (validator == null) {
                return List.of();
            }
            return validator.validate(value).stream()
                    .map(RuleValidator::toViolation)
                    .sorted(Comparator
                            .comparing(RuleViolation::path)
                            .thenComparing(RuleViolation::constraint)
                            .thenComparing(RuleViolation::message))
                    .toList();
        }

        /**
         * Releases the underlying validator factory, when this validator owns one.
         */
        @Override
        public void close() {
            if (cleanable != null) {
                cleanable.clean();
            }
        }

        private static RuleViolation toViolation(ConstraintViolation<?> violation) {
            return new RuleViolation(
                    violation.getPropertyPath().toString(),
                    violation.getMessage(),
                    violation.getMessageTemplate(),
                    violation.getConstraintDescriptor().getAnnotation().annotationType().getName());
        }

        private record ValidatorFactoryCleanup(ValidatorFactory factory) implements Runnable {
            @Override
            public void run() {
                factory.close();
            }
        }
    }
}
