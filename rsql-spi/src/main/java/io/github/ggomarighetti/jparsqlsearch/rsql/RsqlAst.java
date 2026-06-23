package io.github.ggomarighetti.jparsqlsearch.rsql;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.Node;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parsed RSQL tree plus a normalized comparison view for backend integrations. */
public final class RsqlAst {
    private final Node node;
    private final List<RsqlComparison> comparisons;

    private RsqlAst(Node node, List<RsqlComparison> comparisons) {
        this.node = Objects.requireNonNull(node, "node must not be null");
        this.comparisons = List.copyOf(Objects.requireNonNull(comparisons, "comparisons must not be null"));
    }

    /**
     * Creates a normalized AST from a parser-native node.
     *
     * @param node parser-native root node
     * @param operators operator registry used to normalize comparisons
     * @return parsed RSQL tree and its comparison view
     * @throws IllegalArgumentException when a comparison uses an unregistered operator
     */
    public static RsqlAst from(Node node, RsqlOperatorRegistry operators) {
        Objects.requireNonNull(operators, "operators must not be null");
        List<RsqlComparison> comparisons = new ArrayList<>();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (current instanceof ComparisonNode comparison) {
                RsqlOperatorDescriptor descriptor = operators.descriptor(comparison.getOperator())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "operator '%s' is not registered".formatted(comparison.getOperator())));
                comparisons.add(new RsqlComparison(
                        comparison.getSelector(),
                        descriptor.operator(),
                        comparison.getArguments()));
            } else if (current instanceof LogicalNode logical) {
                List<Node> children = logical.getChildren();
                for (int index = children.size() - 1; index >= 0; index--) {
                    stack.push(children.get(index));
                }
            }
        }
        return new RsqlAst(node, comparisons);
    }

    /**
     * Returns the parser-native root.
     *
     * @return parser-native root node
     */
    public Node node() {
        return node;
    }

    /**
     * Returns the normalized comparison view in left-to-right RSQL order.
     *
     * @return immutable normalized comparisons contained in the tree
     */
    public List<RsqlComparison> comparisons() {
        return comparisons;
    }
}
