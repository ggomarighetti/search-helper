package io.github.ggomarighetti.jparsqlsearch.rsql.parser;

import cz.jirutka.rsql.parser.RSQLParser;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;

/** Parses RSQL using the engine's registered operators. */
@FunctionalInterface
public interface RsqlParserFactory {
    /**
     * Returns the parser implementation backed by {@link RSQLParser}.
     *
     * @return stateless default parser factory
     */
    static RsqlParserFactory defaults() {
        return (rsql, operators) ->
                RsqlAst.from(new RSQLParser(operators.parserOperators()).parse(rsql), operators);
    }

    /**
     * Parses and normalizes one RSQL expression.
     *
     * @param rsql RSQL expression
     * @param operators registered logical and parser operators
     * @return normalized AST
     */
    RsqlAst parse(String rsql, RsqlOperatorRegistry operators);
}
