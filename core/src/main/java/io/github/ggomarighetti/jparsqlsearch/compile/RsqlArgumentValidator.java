package io.github.ggomarighetti.jparsqlsearch.compile;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterValidationResult;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.core.convert.ConversionService;

final class RsqlArgumentValidator {
    private final ConversionService conversionService;

    RsqlArgumentValidator(ConversionService conversionService) {
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService must not be null");
    }

    List<RsqlValidationError> validate(
            SearchField<?> field,
            RsqlOperator operator,
            List<String> arguments,
            String astPath) {
        var result = field.filtering().operators().get(operator).validate(arguments, conversionService);
        List<RsqlValidationError> errors = new ArrayList<>();
        for (FilterValidationResult.Error error : result.errors()) {
            errors.add(toValidationError(field, operator, astPath, error));
        }
        return errors;
    }

    private RsqlValidationError toValidationError(
            SearchField<?> field,
            RsqlOperator operator,
            String astPath,
            FilterValidationResult.Error error) {
        if (error.code() == FilterValidationResult.Error.Code.CONVERSION_FAILED) {
            return RsqlValidationErrors.conversionFailed(field, operator, astPath, error);
        }
        if (error.code() == FilterValidationResult.Error.Code.ARGUMENT_RULE) {
            return RsqlValidationErrors.argumentRule(field, operator, astPath, error);
        }
        return RsqlValidationErrors.argumentsRule(field, operator, astPath, error);
    }
}
