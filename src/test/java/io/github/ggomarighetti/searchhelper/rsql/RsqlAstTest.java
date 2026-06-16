package io.github.ggomarighetti.searchhelper.rsql;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import io.github.ggomarighetti.searchhelper.rsql.operator.RsqlOperatorRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsqlAstTest {
    @Test
    @SuppressWarnings("deprecation")
    void rejectsComparisonNodesWithUnregisteredOperators() {
        ComparisonNode node = new ComparisonNode(
                new ComparisonOperator("=missing=", true),
                "email",
                List.of("person@example.com"));

        assertThrows(IllegalArgumentException.class, () -> RsqlAst.from(node, new RsqlOperatorRegistry(List.of())));
    }

    @Test
    void ignoresUnsupportedNodesWhenBuildingComparisonView() {
        RsqlAst ast = RsqlAst.from(new UnsupportedNode(), new RsqlOperatorRegistry(List.of()));

        assertEquals(List.of(), ast.comparisons());
    }

    private static final class UnsupportedNode implements Node {
        @Override
        public <R, A> R accept(RSQLVisitor<R, A> visitor, A param) {
            return null;
        }

        @Override
        public <R, A> R accept(RSQLVisitor<R, A> visitor) {
            return null;
        }
    }
}
