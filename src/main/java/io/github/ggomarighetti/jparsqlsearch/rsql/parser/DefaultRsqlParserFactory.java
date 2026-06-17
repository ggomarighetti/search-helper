package io.github.ggomarighetti.jparsqlsearch.rsql.parser;

import cz.jirutka.rsql.parser.RSQLParser;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperatorRegistry;

/** Default parser factory backed by {@link RSQLParser}. */
public final class DefaultRsqlParserFactory implements RsqlParserFactory {
    /** Creates the default parser factory. */
    public DefaultRsqlParserFactory() {
        // Stateless factory; no initialization is required.
    }

    @Override
    public RSQLParser create(RsqlOperatorRegistry operators) {
        return new RSQLParser(operators.parserOperators());
    }
}
