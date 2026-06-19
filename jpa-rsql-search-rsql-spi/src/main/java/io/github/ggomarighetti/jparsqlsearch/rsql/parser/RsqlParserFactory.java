package io.github.ggomarighetti.jparsqlsearch.rsql.parser;

import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;

/** Parses RSQL using the engine's registered operators. */
@FunctionalInterface
public interface RsqlParserFactory {
    /**
     * Parses and normalizes one RSQL expression.
     *
     * @param rsql RSQL expression
     * @param operators registered logical and parser operators
     * @return normalized AST
     */
    RsqlAst parse(String rsql, RsqlOperatorRegistry operators);
}
