package io.github.ggomarighetti.jparsqlsearch.compile;

import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlFilterValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.validation.RsqlValidationError;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.ConversionService;

final class RsqlRulesValidator {
    private final SearchDefinition<?> definition;
    private final SearchPolicy.Rsql limits;
    private final SearchProtectionContext protection;
    private final RsqlOperatorRegistry operators;
    private final RsqlArgumentValidator argumentValidator;
    private final RsqlOrAnalyzer orAnalyzer;

    public RsqlRulesValidator(
            SearchDefinition<?> definition,
            ConversionService conversionService,
            SearchPolicy.Rsql limits,
            SearchProtectionContext protection,
            RsqlOperatorRegistry operators) {
        this.definition = definition;
        this.limits = limits;
        this.protection = protection;
        this.operators = operators;
        this.argumentValidator = new RsqlArgumentValidator(conversionService);
        this.orAnalyzer = new RsqlOrAnalyzer(definition);
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
            errors.addAll(argumentValidator.validate(field, descriptor.operator(), arguments, path));
        }
    }

    private RsqlOperatorDescriptor descriptor(
            ComparisonNode comparison,
            String path,
            List<RsqlValidationError> errors) {
        RsqlOperatorDescriptor descriptor = operators.descriptor(comparison.getOperator()).orElse(null);
        if (descriptor == null) {
            errors.add(RsqlValidationErrors.unregisteredOperator(comparison, path));
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
        errors.add(RsqlValidationErrors.invalidArity(comparison, descriptor, arguments.size(), path));
        return false;
    }

    private SearchField<?> field(
            ComparisonNode comparison,
            RsqlOperatorDescriptor descriptor,
            String path,
            List<RsqlValidationError> errors) {
        SearchField<?> field = definition.field(comparison.getSelector()).orElse(null);
        if (field == null) {
            errors.add(RsqlValidationErrors.undeclaredSelector(comparison, descriptor, path));
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
            errors.add(RsqlValidationErrors.filteringDisabled(comparison, descriptor, path));
            return false;
        }
        if (!field.filtering().allows(descriptor.operator())) {
            errors.add(RsqlValidationErrors.disallowedOperator(comparison, descriptor, path));
            return false;
        }
        return true;
    }

    private void validateOrNode(
            OrNode orNode,
            Entry entry,
            ArrayDeque<Entry> stack) {
        List<Node> children = orNode.getChildren();
        RsqlOrAnalyzer.Metadata metadata = orMetadata(orNode);
        protection.recordOr(children.size(), metadata.selectors().size(), metadata.joinRoots().size());
        pushChildren(stack, children, entry.depth(), entry.path());
    }

    private RsqlValidationError unsupportedNode(String path) {
        return RsqlValidationErrors.unsupportedNode(path);
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

    private RsqlOrAnalyzer.Metadata orMetadata(OrNode root) {
        return orAnalyzer.metadata(root);
    }

    private RsqlFilterValidationException limitExceeded() {
        return new RsqlFilterValidationException(
                RsqlFilterValidationException.LIMIT_EXCEEDED,
                "RSQL filter exceeds configured safety limits.");
    }

    private record Entry(Node node, int depth, String path) {
    }

}
