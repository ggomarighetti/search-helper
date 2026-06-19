package io.github.ggomarighetti.jparsqlsearch.compile;

import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.exception.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.exception.RsqlValidationError;
import io.github.ggomarighetti.jparsqlsearch.filter.FilterValidationError;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperatorRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.ConversionService;

final class RsqlRulesValidator {
    private final SearchDefinition<?> definition;
    private final ConversionService conversionService;
    private final SearchPolicy.Rsql limits;
    private final SearchProtectionContext protection;
    private final RsqlOperatorRegistry operators;

    public RsqlRulesValidator(
            SearchDefinition<?> definition,
            ConversionService conversionService,
            SearchPolicy.Rsql limits,
            SearchProtectionContext protection,
            RsqlOperatorRegistry operators) {
        this.definition = definition;
        this.conversionService = conversionService;
        this.limits = limits;
        this.protection = protection;
        this.operators = operators;
    }

    public List<RsqlValidationError> validate(RsqlAst ast) {
        ArrayDeque<Entry> stack = new ArrayDeque<>();
        stack.push(new Entry(ast.node(), 1, "$"));
        List<RsqlValidationError> errors = new ArrayList<>();

        int nodes = 0;

        while (!stack.isEmpty()) {
            Entry entry = stack.pop();
            nodes = validateNodeBudget(entry, nodes + 1);
            validateNode(entry, stack, errors);
        }
        return List.copyOf(errors);
    }

    private int validateNodeBudget(Entry entry, int nodes) {
        if (nodes > limits.maxNodes() || entry.depth() > limits.maxDepth()) {
            throw limitExceeded();
        }
        return nodes;
    }

    private void validateNode(
            Entry entry,
            ArrayDeque<Entry> stack,
            List<RsqlValidationError> errors) {
        Node node = entry.node();
        if (node instanceof ComparisonNode comparison) {
            validateComparison(comparison, entry.path(), errors);
        } else if (node instanceof AndNode andNode) {
            pushChildren(stack, andNode.getChildren(), entry.depth(), entry.path());
        } else if (node instanceof OrNode orNode) {
            validateOrNode(orNode, entry, stack);
        } else {
            errors.add(unsupportedNode(entry.path()));
        }
    }

    private void validateComparison(
            ComparisonNode comparison,
            String path,
            List<RsqlValidationError> errors) {
        RsqlOperatorDescriptor descriptor = descriptor(comparison, path, errors);
        if (descriptor == null) {
            return;
        }

        List<String> arguments = comparison.getArguments();
        boolean arityAccepted = validateArity(comparison, descriptor, arguments, path, errors);
        SearchField<?> field = field(comparison, descriptor, path, errors);
        if (field == null) {
            return;
        }

        // Enforce request protection before selector-specific semantic checks so
        // declared selectors cannot hide oversized arguments behind invalid input.
        protection.recordComparison(field, descriptor, arguments.size(), arguments);
        if (!isFilteringAllowed(field, comparison, descriptor, path, errors)) {
            return;
        }

        if (arityAccepted) {
            errors.addAll(validateArguments(field, descriptor.operator(), arguments, path));
        }
    }

    private RsqlOperatorDescriptor descriptor(
            ComparisonNode comparison,
            String path,
            List<RsqlValidationError> errors) {
        RsqlOperatorDescriptor descriptor = operators.descriptor(comparison.getOperator()).orElse(null);
        if (descriptor == null) {
            errors.add(unregisteredOperator(comparison, path));
        }
        return descriptor;
    }

    private boolean validateArity(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            List<String> arguments,
            String path,
            List<RsqlValidationError> errors) {
        if (descriptor.arity().accepts(arguments.size())) {
            return true;
        }
        errors.add(invalidArity(comparison, descriptor, arguments.size(), path));
        return false;
    }

    private SearchField<?> field(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path,
            List<RsqlValidationError> errors) {
        SearchField<?> field = definition.field(comparison.getSelector()).orElse(null);
        if (field == null) {
            errors.add(undeclaredSelector(comparison, descriptor, path));
        }
        return field;
    }

    private boolean isFilteringAllowed(
            SearchField<?> field,
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path,
            List<RsqlValidationError> errors) {
        if (!field.filtering().enabled()) {
            errors.add(filteringDisabled(comparison, descriptor, path));
            return false;
        }
        if (!field.filtering().allows(descriptor.operator())) {
            errors.add(disallowedOperator(comparison, descriptor, path));
            return false;
        }
        return true;
    }

    private void validateOrNode(
            OrNode orNode,
            Entry entry,
            ArrayDeque<Entry> stack) {
        List<Node> children = orNode.getChildren();
        OrMetadata metadata = orMetadata(orNode);
        protection.recordOr(children.size(), metadata.selectors().size(), metadata.joinRoots().size());
        pushChildren(stack, children, entry.depth(), entry.path());
    }

    private RsqlValidationError unregisteredOperator(ComparisonNode comparison, String path) {
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

    private RsqlValidationError invalidArity(
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

    private RsqlValidationError undeclaredSelector(
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

    private RsqlValidationError filteringDisabled(
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

    private RsqlValidationError disallowedOperator(
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

    private RsqlValidationError unsupportedNode(String path) {
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

    private void pushChildren(
            ArrayDeque<Entry> stack,
            List<Node> children,
            int parentDepth,
            String parentPath) {
        if (children.size() > limits.maxLogicalChildren()) {
            throw limitExceeded();
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            stack.push(new Entry(
                    children.get(index),
                    parentDepth + 1,
                    parentPath + ".children[" + index + "]"));
        }
    }

    private List<RsqlValidationError> validateArguments(
            SearchField<?> field,
            RsqlOperator operator,
            List<String> arguments,
            String astPath) {
        var result = field.filtering().operators().get(operator).validate(arguments, conversionService);
        List<RsqlValidationError> errors = new ArrayList<>();
        for (FilterValidationError error : result.errors()) {
            if (error.code() == FilterValidationError.Code.CONVERSION_FAILED) {
                errors.add(new RsqlValidationError(
                        RsqlValidationError.ARGUMENT_CONVERSION_FAILED,
                        astPath + ".arguments[" + error.argumentIndex() + "]",
                        field.selector(),
                        operator.name(),
                        error.argumentIndex(),
                        null,
                        "Argument could not be converted to '%s'.".formatted(error.targetType()),
                        null,
                        null));
            } else if (error.code() == FilterValidationError.Code.ARGUMENT_RULE) {
                var violation = error.violation();
                errors.add(new RsqlValidationError(
                        RsqlValidationError.ARGUMENT_RULE_VIOLATION,
                        astPath + ".arguments[" + error.argumentIndex() + "]",
                        field.selector(),
                        operator.name(),
                        error.argumentIndex(),
                        violation.path(),
                        violation.message(),
                        violation.messageTemplate(),
                        violation.constraint()));
            } else {
                var violation = error.violation();
                errors.add(new RsqlValidationError(
                        RsqlValidationError.ARGUMENTS_RULE_VIOLATION,
                        astPath + ".arguments",
                        field.selector(),
                        operator.name(),
                        null,
                        violation.path(),
                        violation.message(),
                        violation.messageTemplate(),
                        violation.constraint()));
            }
        }
        return errors;
    }

    private OrMetadata orMetadata(OrNode root) {
        Set<String> selectors = new LinkedHashSet<>();
        Set<String> joinRoots = new LinkedHashSet<>();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node instanceof ComparisonNode comparison) {
                selectors.add(comparison.getSelector());
                definition.field(comparison.getSelector())
                        .map(SearchField::filtering)
                        .ifPresent(filtering -> filtering.topology().joinedPaths().stream()
                                .map(RsqlRulesValidator::rootPath)
                                .forEach(joinRoots::add));
            } else if (node instanceof cz.jirutka.rsql.parser.ast.LogicalNode logical) {
                for (Node child : logical.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return new OrMetadata(selectors, joinRoots);
    }

    private static String rootPath(String path) {
        int separator = path.indexOf('.');
        return separator < 0 ? path : path.substring(0, separator);
    }

    private RsqlFilterValidationException limitExceeded() {
        return new RsqlFilterValidationException(
                RsqlFilterValidationException.LIMIT_EXCEEDED,
                "RSQL filter exceeds configured safety limits.");
    }

    private record Entry(Node node, int depth, String path) {
    }

    private record OrMetadata(Set<String> selectors, Set<String> joinRoots) {
    }
}
