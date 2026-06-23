package io.github.ggomarighetti.jparsqlsearch.compile;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinition;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class RsqlOrAnalyzer {
    private final SearchDefinition<?> definition;

    RsqlOrAnalyzer(SearchDefinition<?> definition) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
    }

    Metadata metadata(OrNode root) {
        Set<String> selectors = new LinkedHashSet<>();
        Set<String> joinRoots = new LinkedHashSet<>();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node instanceof ComparisonNode comparison) {
                recordComparison(selectors, joinRoots, comparison);
            } else if (node instanceof LogicalNode logical) {
                pushChildren(stack, logical);
            }
        }
        return new Metadata(selectors, joinRoots);
    }

    static String rootPath(String path) {
        int separator = path.indexOf('.');
        return separator < 0 ? path : path.substring(0, separator);
    }

    private void recordComparison(
            Set<String> selectors,
            Set<String> joinRoots,
            ComparisonNode comparison) {
        selectors.add(comparison.getSelector());
        definition.field(comparison.getSelector())
                .map(SearchField::filtering)
                .ifPresent(filtering -> filtering.topology().joinedPaths().stream()
                        .map(RsqlOrAnalyzer::rootPath)
                        .forEach(joinRoots::add));
    }

    private static void pushChildren(ArrayDeque<Node> stack, LogicalNode logical) {
        for (Node child : logical.getChildren()) {
            stack.push(child);
        }
    }

    record Metadata(Set<String> selectors, Set<String> joinRoots) {
    }
}
