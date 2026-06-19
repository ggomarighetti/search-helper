package io.github.ggomarighetti.jparsqlsearch.rsql.parser;

import cz.jirutka.rsql.parser.RSQLParser;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlAst;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;

/** Default parser factory backed by {@link RSQLParser}. */
public final class DefaultRsqlParserFactory implements RsqlParserFactory {
    /** Creates the default parser factory. */
    public DefaultRsqlParserFactory() {
        // Stateless factory; no initialization is required.
    }

    @Override
    public RsqlAst parse(String rsql, RsqlOperatorRegistry operators) {
        return RsqlAst.from(new RSQLParser(operators.parserOperators()).parse(rsql), operators);
    }
}
