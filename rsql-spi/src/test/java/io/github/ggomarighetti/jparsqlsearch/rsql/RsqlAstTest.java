package io.github.ggomarighetti.jparsqlsearch.rsql;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.jparsqlsearch.rsql.parser.RsqlParserFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.github.ggomarighetti.jparsqlsearch.unit.ExceptionAssertions.thrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RsqlAstTest {
    @Test
    @SuppressWarnings("deprecation")
    void rejectsComparisonNodesWithUnregisteredOperators() {
        ComparisonNode node = new ComparisonNode(
                new ComparisonOperator("=missing=", true),
                "email",
                List.of("person@example.com"));

        assertNotNull(thrownBy(
                IllegalArgumentException.class,
                () -> RsqlAst.from(node, new RsqlOperatorRegistry(List.of()))));
    }

    @Test
    void ignoresUnsupportedNodesWhenBuildingComparisonView() {
        RsqlAst ast = RsqlAst.from(new UnsupportedNode(), new RsqlOperatorRegistry(List.of()));

        assertEquals(List.of(), ast.comparisons());
    }

    @Test
    void preservesLeftToRightComparisonOrder() {
        RsqlOperatorRegistry operators = new RsqlOperatorRegistry(DefaultRsqlOperatorDescriptors.all());
        RsqlAst ast = RsqlParserFactory.defaults()
                .parse("email==a;name==b;taxId==c", operators);

        assertEquals(
                List.of("email", "name", "taxId"),
                ast.comparisons().stream().map(RsqlComparison::selector).toList());
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
