package io.github.ggomarighetti.jparsqlsearch.compile;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterValidationResult;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlValidationError;

final class RsqlValidationErrors {
    private RsqlValidationErrors() {
    }

    static RsqlValidationError unregisteredOperator(ComparisonNode comparison, String path) {
        return new RsqlValidationError(
                RsqlValidationError.OPERATOR_NOT_ALLOWED,
                path + ".operator",
                comparison.getSelector(),
                comparison.getOperator().toString(),
                null,
                null,
                "Operator is not registered.",
                null,
                null);
    }

    static RsqlValidationError invalidArity(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            int argumentCount,
            String path) {
        return new RsqlValidationError(
                RsqlValidationError.OPERATOR_INVALID_ARITY,
                path + ".arguments",
                comparison.getSelector(),
                descriptor.operator().name(),
                null,
                null,
                "Operator expects between %d and %d arguments but received %d."
                        .formatted(
                                descriptor.arity().min(),
                                descriptor.arity().max(),
                                argumentCount),
                null,
                null);
    }

    static RsqlValidationError undeclaredSelector(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path) {
        return new RsqlValidationError(
                RsqlValidationError.FIELD_NOT_ALLOWED,
                path + ".selector",
                comparison.getSelector(),
                descriptor.operator().name(),
                null,
                null,
                "Selector is not declared by the search definition.",
                null,
                null);
    }

    static RsqlValidationError filteringDisabled(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path) {
        return new RsqlValidationError(
                RsqlValidationError.FILTERING_DISABLED,
                path + ".selector",
                comparison.getSelector(),
                descriptor.operator().name(),
                null,
                null,
                "Selector is declared but filtering is disabled.",
                null,
                null);
    }

    static RsqlValidationError disallowedOperator(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path) {
        return new RsqlValidationError(
                RsqlValidationError.OPERATOR_NOT_ALLOWED,
                path + ".operator",
                comparison.getSelector(),
                descriptor.operator().name(),
                null,
                null,
                "Operator is not allowed for this selector.",
                null,
                null);
    }

    static RsqlValidationError unsupportedNode(String path) {
        return new RsqlValidationError(
                RsqlValidationError.FIELD_NOT_ALLOWED,
                path,
                null,
                null,
                null,
                null,
                "AST node type is not supported.",
                null,
                null);
    }

    static RsqlValidationError conversionFailed(
            SearchField<?> field,
            RsqlOperator operator,
            String astPath,
            FilterValidationResult.Error error) {
        return new RsqlValidationError(
                RsqlValidationError.ARGUMENT_CONVERSION_FAILED,
                astPath + ".arguments[" + error.argumentIndex() + "]",
                field.selector(),
                operator.name(),
                error.argumentIndex(),
                null,
                "Argument could not be converted to '%s'.".formatted(error.targetType()),
                null,
                null);
    }

    static RsqlValidationError argumentRule(
            SearchField<?> field,
            RsqlOperator operator,
            String astPath,
            FilterValidationResult.Error error) {
        var violation = error.violation();
        return new RsqlValidationError(
                RsqlValidationError.ARGUMENT_RULE_VIOLATION,
                astPath + ".arguments[" + error.argumentIndex() + "]",
                field.selector(),
                operator.name(),
                error.argumentIndex(),
                violation.path(),
                violation.message(),
                violation.messageTemplate(),
                violation.constraint());
    }

    static RsqlValidationError argumentsRule(
            SearchField<?> field,
            RsqlOperator operator,
            String astPath,
            FilterValidationResult.Error error) {
        var violation = error.violation();
        return new RsqlValidationError(
                RsqlValidationError.ARGUMENTS_RULE_VIOLATION,
                astPath + ".arguments",
                field.selector(),
                operator.name(),
                null,
                violation.path(),
                violation.message(),
                violation.messageTemplate(),
                violation.constraint());
    }
}
