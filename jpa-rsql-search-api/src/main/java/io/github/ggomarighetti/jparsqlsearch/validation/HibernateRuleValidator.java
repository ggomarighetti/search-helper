package io.github.ggomarighetti.jparsqlsearch.validation;

import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.ref.Cleaner;
import java.util.Comparator;
import java.util.List;
import org.hibernate.validator.cfg.ConstraintDef;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Executes programmatic Hibernate Validator constraints without exposing invalid values.
 *
 * @param <T> validated value type
 */
public final class HibernateRuleValidator<T> implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final HibernateRuleValidator<?> NONE = new HibernateRuleValidator<>(null, null);

    private final Validator validator;
    @SuppressWarnings("unused")
    private final Cleaner.Cleanable cleanable;

    private HibernateRuleValidator(Validator validator, ValidatorFactory factory) {
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
    public static <T> HibernateRuleValidator<T> none() {
        return (HibernateRuleValidator<T>) NONE;
    }

    /**
     * Creates a validator for programmatically declared type constraints.
     *
     * @param type runtime type receiving the constraints
     * @param rules constraint definitions
     * @param <T> validated value type
     * @return configured validator, or {@link #none()} for an empty rule list
     */
    public static <T> HibernateRuleValidator<T> forType(Class<?> type, List<ConstraintDef<?, ?>> rules) {
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
        return new HibernateRuleValidator<>(factory.getValidator(), factory);
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
                .map(HibernateRuleValidator::toViolation)
                .sorted(Comparator
                        .comparing(RuleViolation::path)
                        .thenComparing(RuleViolation::constraint)
                        .thenComparing(RuleViolation::message))
                .toList();
    }

    /**
     * Releases the underlying validator factory, when this validator owns one.
     *
     * <p>Reusable static validators created with {@link #none()} do not own resources
     * and closing them is a no-op. Closing is idempotent.
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
