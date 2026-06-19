package io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub;

import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import io.github.ggomarighetti.jparsqlsearch.definition.SearchField;
import io.github.ggomarighetti.jparsqlsearch.exception.SearchDefinitionValidationException;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendValidationContext;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorBinding;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaPredicateContext;
import io.github.ggomarighetti.jparsqlsearch.rsql.jpa.RsqlJpaPredicateFactory;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.DefaultRsqlOperatorDescriptors;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorDescriptor;
import io.github.ggomarighetti.jparsqlsearch.rsql.metadata.RsqlOperatorRegistry;
import io.github.ggomarighetti.jparsqlsearch.rsql.RsqlCompilationRequest;
import io.github.perplexhub.rsql.JsonbConfiguration;
import io.github.perplexhub.rsql.RSQLCustomPredicate;
import io.github.perplexhub.rsql.RSQLCustomPredicateInput;
import io.github.perplexhub.rsql.RSQLJPAPredicateConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/** Built-in RSQL backend implemented with Perplexhub's JPA predicate converter. */
public final class PerplexhubRsqlBackendAdapter implements RsqlBackendAdapter {
    private final PerplexhubRsqlBackendOptions options;

    /** Creates the adapter with {@link PerplexhubRsqlBackendOptions#defaults()}. */
    public PerplexhubRsqlBackendAdapter() {
        this(PerplexhubRsqlBackendOptions.defaults());
    }

    /**
     * Creates an adapter with explicit options.
     *
     * @param options immutable backend options
     */
    public PerplexhubRsqlBackendAdapter(PerplexhubRsqlBackendOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    public <T> Specification<T> compile(RsqlCompilationRequest<T> request) {
        return (root, query, criteriaBuilder) -> {
            if (request.distinct()) {
                query.distinct(true);
            }
            PredicateContext context = new PredicateContext(request, root, criteriaBuilder);
            return context.predicate(request.ast().node());
        };
    }

    @Override
    public void validate(RsqlBackendValidationContext context) {
        for (RsqlOperator operator : context.definition().filteringOperators()) {
            RsqlOperatorDescriptor descriptor = context.operators().require(operator);
            boolean custom = context.jpaOperators().predicate(operator).isPresent();
            if (!DefaultRsqlOperatorDescriptors.isDefault(operator) && !custom) {
                throw new SearchDefinitionValidationException(
                        SearchDefinitionValidationException.RSQL_OPERATOR_NOT_EXECUTABLE,
                        "operator '%s' is registered but has no Perplexhub JPA predicate".formatted(operator));
            }
            if (custom && descriptor.argumentType().isEmpty()) {
                throw new SearchDefinitionValidationException(
                        SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH,
                        "custom operator '%s' must declare an argument type".formatted(operator));
            }
            if (custom
                    && !Comparable.class.isAssignableFrom(descriptor.argumentType().orElseThrow())) {
                throw new SearchDefinitionValidationException(
                        SearchDefinitionValidationException.RSQL_OPERATOR_TYPE_MISMATCH,
                        "custom operator '%s' must declare a Comparable argument type for the Perplexhub backend"
                                .formatted(operator));
            }
        }
    }

    private static List<RSQLCustomPredicate<?>> customPredicates(
            RsqlOperatorRegistry operators,
            RsqlJpaOperatorRegistry jpaOperators) {
        List<RSQLCustomPredicate<?>> predicates = new ArrayList<>();
        for (RsqlJpaOperatorBinding binding : jpaOperators.bindings()) {
            predicates.add(customPredicate(
                    operators.require(binding.operator()),
                    binding.predicateFactory()));
        }
        return predicates;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RSQLCustomPredicate<?> customPredicate(
            RsqlOperatorDescriptor descriptor,
            RsqlJpaPredicateFactory factory) {
        Class<? extends Comparable<?>> type = comparableType(descriptor.argumentType().orElse(String.class));
        Function<RSQLCustomPredicateInput, Predicate> converter =
                input -> factory.toPredicate(context(input, descriptor.operator()));
        return new RSQLCustomPredicate(
                descriptor.comparisonOperator(),
                type,
                converter);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Comparable<?>> comparableType(Class<?> type) {
        return (Class<? extends Comparable<?>>) type.asSubclass(Comparable.class);
    }

    private static RsqlJpaPredicateContext<?, ?, ?, ?, ?> context(RSQLCustomPredicateInput input, RsqlOperator operator) {
        return new RsqlJpaPredicateContext<>(
                input.getCriteriaBuilder(),
                input.getPath(),
                input.getAttribute(),
                input.getArguments(),
                input.getRoot(),
                operator);
    }

    private final class PredicateContext {
        private final RsqlCompilationRequest<?> request;
        private final CriteriaBuilder criteriaBuilder;
        private final Map<Class<?>, From<?, ?>> roots = new LinkedHashMap<>();
        private final Map<Class<?>, RSQLJPAPredicateConverter> converters = new LinkedHashMap<>();
        private final List<RSQLCustomPredicate<?>> customPredicates;

        private PredicateContext(
                RsqlCompilationRequest<?> request,
                Root<?> root,
                CriteriaBuilder criteriaBuilder) {
            this.request = request;
            this.criteriaBuilder = criteriaBuilder;
            this.roots.put(request.definition().entity(), root);
            this.customPredicates = customPredicates(request.operators(), request.jpaOperators());
        }

        private Predicate predicate(Node node) {
            if (node instanceof ComparisonNode comparison) {
                SearchField<?> field = request.definition()
                        .field(comparison.getSelector())
                        .orElseThrow(() -> new IllegalStateException(
                                "validated selector '%s' is missing".formatted(comparison.getSelector())));
                Class<?> rootType = field.subtype().orElse(request.definition().entity());
                From<?, ?> comparisonRoot = roots.computeIfAbsent(rootType, this::treatedRoot);
                RSQLJPAPredicateConverter converter =
                        converters.computeIfAbsent(rootType, ignored -> converter());
                return accept(comparison, converter, comparisonRoot);
            }
            if (node instanceof AndNode andNode) {
                return criteriaBuilder.and(andNode.getChildren().stream()
                        .map(this::predicate)
                        .toArray(Predicate[]::new));
            }
            if (node instanceof OrNode orNode) {
                return criteriaBuilder.or(orNode.getChildren().stream()
                        .map(this::predicate)
                        .toArray(Predicate[]::new));
            }
            throw new IllegalArgumentException(
                    "Unsupported validated RSQL AST node '%s'".formatted(node.getClass().getName()));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private From<?, ?> treatedRoot(Class<?> subtype) {
            Root<?> root = (Root<?>) roots.get(request.definition().entity());
            return criteriaBuilder.treat((Root) root, (Class) subtype);
        }

        private RSQLJPAPredicateConverter converter() {
            return new RSQLJPAPredicateConverter(
                    criteriaBuilder,
                    request.definition().filteringPaths(),
                    customPredicates,
                    null,
                    null,
                    null,
                    options.strictEquality(),
                    options.likeEscapeCharacter(),
                    JsonbConfiguration.DEFAULT,
                    request.conversionService());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Predicate accept(
                ComparisonNode comparison,
                RSQLJPAPredicateConverter converter,
                From<?, ?> root) {
            return (Predicate) comparison.accept(converter, root);
        }
    }
}
