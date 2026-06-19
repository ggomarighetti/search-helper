package io.github.ggomarighetti.jparsqlsearch.rsql;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.parser.DefaultRsqlParserFactory;
import io.github.ggomarighetti.jparsqlsearch.rsql.parser.RsqlParserFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;

/** Builder for {@link SearchRsqlEngine}. */
public final class SearchRsqlEngineBuilder {
    private final List<RsqlOperatorDescriptor> operators = new ArrayList<>();
    private RsqlParserFactory parserFactory = new DefaultRsqlParserFactory();
    private RsqlBackendAdapter backend = new PerplexhubRsqlBackendAdapter();
    private ConversionService conversionService = ApplicationConversionService.getSharedInstance();

    SearchRsqlEngineBuilder() {
        operators.addAll(DefaultRsqlOperatorDescriptors.all());
    }

    /**
     * Removes the default operator descriptors so callers can build a custom dialect
     * from an empty registry.
     *
     * @return this builder
     */
    public SearchRsqlEngineBuilder withoutDefaultOperators() {
        operators.clear();
        return this;
    }

    /**
     * Adds one operator descriptor.
     *
     * @param descriptor operator descriptor
     * @return this builder
     */
    public SearchRsqlEngineBuilder operator(RsqlOperatorDescriptor descriptor) {
        operators.add(Objects.requireNonNull(descriptor, "descriptor must not be null"));
        return this;
    }

    /**
     * Adds operator descriptors.
     *
     * @param descriptors descriptors to add
     * @return this builder
     */
    public SearchRsqlEngineBuilder operators(Collection<RsqlOperatorDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors must not be null");
        descriptors.forEach(this::operator);
        return this;
    }

    /**
     * Replaces the parser factory.
     *
     * @param parserFactory parser factory
     * @return this builder
     */
    public SearchRsqlEngineBuilder parserFactory(RsqlParserFactory parserFactory) {
        this.parserFactory = Objects.requireNonNull(parserFactory, "parserFactory must not be null");
        return this;
    }

    /**
     * Replaces the backend adapter.
     *
     * @param backend backend adapter
     * @return this builder
     */
    public SearchRsqlEngineBuilder backend(RsqlBackendAdapter backend) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        return this;
    }

    /**
     * Replaces the conversion service used by validation and compilation.
     *
     * @param conversionService conversion service
     * @return this builder
     */
    public SearchRsqlEngineBuilder conversionService(ConversionService conversionService) {
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService must not be null");
        return this;
    }

    /**
     * Builds an engine.
     *
     * @return configured engine
     */
    public SearchRsqlEngine build() {
        RsqlOperatorRegistry registry = new RsqlOperatorRegistry(operators);
        return new SearchRsqlEngine(registry, parserFactory, backend, conversionService);
    }
}
