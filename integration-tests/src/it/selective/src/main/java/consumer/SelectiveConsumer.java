package consumer;

import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlEngines;

final class SelectiveConsumer {
    private final SearchCompiler compiler =
            new SearchCompiler(PerplexhubRsqlEngines.defaults(), SearchPolicy.defaults());
}
