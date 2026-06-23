package io.github.ggomarighetti.jparsqlsearch.rsql.engine;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorBinding;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaPredicateFactory;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.parser.RsqlParserFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;

/** Builder for {@link SearchRsqlEngine}. */
public final class SearchRsqlEngineBuilder {
    private final List<RsqlOperatorDescriptor> operators = new ArrayList<>();
    private final List<RsqlJpaOperatorBinding> jpaOperators = new ArrayList<>();
    private RsqlParserFactory parserFactory = RsqlParserFactory.defaults();
    private RsqlBackendAdapter backend;
    private ConversionService conversionService = new DefaultFormattingConversionService();

    SearchRsqlEngineBuilder(RsqlBackendAdapter backend) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
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
     * Adds a descriptor and its custom JPA execution atomically.
     *
     * @param descriptor neutral operator descriptor
     * @param predicateFactory custom JPA predicate
     * @return this builder
     */
    public SearchRsqlEngineBuilder operator(
            RsqlOperatorDescriptor descriptor,
            RsqlJpaPredicateFactory predicateFactory) {
        operator(descriptor);
        return jpaPredicate(descriptor.operator(), predicateFactory);
    }

    /**
     * Registers custom JPA execution for a logical operator.
     *
     * @param operator registered logical operator
     * @param predicateFactory custom predicate factory
     * @return this builder
     */
    public SearchRsqlEngineBuilder jpaPredicate(
            RsqlOperator operator,
            RsqlJpaPredicateFactory predicateFactory) {
        jpaOperators.add(new RsqlJpaOperatorBinding(operator, predicateFactory));
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
     * Replaces the explicitly selected backend.
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
        RsqlJpaOperatorRegistry jpaRegistry = new RsqlJpaOperatorRegistry(jpaOperators);
        for (RsqlJpaOperatorBinding binding : jpaRegistry.bindings()) {
            if (registry.descriptor(binding.operator()).isEmpty()) {
                throw new IllegalArgumentException(
                        "JPA predicate operator '%s' is not registered".formatted(binding.operator()));
            }
        }
        return new SearchRsqlEngine(registry, jpaRegistry, parserFactory, backend, conversionService);
    }
}
