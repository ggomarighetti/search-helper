package io.github.ggomarighetti.jparsqlsearch.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Immutable global limits and cross-component search protection rules. */
public final class SearchPolicy {
    private static final String CUSTOMIZER_MUST_NOT_BE_NULL = "customizer must not be null";

    private static final SearchPolicy DEFAULTS = SearchPolicy.builder().build();

    private final Rsql rsql;
    private final Filter filter;
    private final Paging paging;
    private final Sorting sorting;
    private final Query query;
    private final Paths paths;

    private SearchPolicy(
            Rsql rsql,
            Filter filter,
            Paging paging,
            Sorting sorting,
            Query query,
            Paths paths) {
        this.rsql = Objects.requireNonNull(rsql, "rsql must not be null");
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
        this.paging = Objects.requireNonNull(paging, "paging must not be null");
        this.sorting = Objects.requireNonNull(sorting, "sorting must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.paths = Objects.requireNonNull(paths, "paths must not be null");
    }

    /**
     * Returns hardened defaults.
     *
     * @return shared immutable default policy
     */
    public static SearchPolicy defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a policy builder initialized with defaults.
     *
     * @return new policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Copies this policy into a mutable builder.
     *
     * @return builder initialized with every current value
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.rsql.maxLength = rsql.maxLength();
        builder.rsql.maxParenthesesDepth = rsql.maxParenthesesDepth();
        builder.rsql.maxNodes = rsql.maxNodes();
        builder.rsql.maxDepth = rsql.maxDepth();
        builder.rsql.maxLogicalChildren = rsql.maxLogicalChildren();
        builder.filter.maxComparisons = filter.maxComparisons();
        builder.filter.maxComparisonsPerSelector = filter.maxComparisonsPerSelector();
        builder.filter.maxArgumentsPerComparison = filter.maxArgumentsPerComparison();
        builder.filter.maxArgumentsTotal = filter.maxArgumentsTotal();
        builder.filter.maxArgumentLength = filter.maxArgumentLength();
        builder.filter.maxInValues = filter.maxInValues();
        builder.filter.maxNotInValues = filter.maxNotInValues();
        builder.filter.maxBetweenRanges = filter.maxBetweenRanges();
        builder.filter.maxNegatedComparisons = filter.maxNegatedComparisons();
        builder.filter.maxOrBranches = filter.maxOrBranches();
        builder.filter.maxOrSelectors = filter.maxOrSelectors();
        builder.filter.maxOrJoinRoots = filter.maxOrJoinRoots();
        builder.filter.maxHeterogeneousOrBranches = filter.maxHeterogeneousOrBranches();
        builder.filter.maxJoinedPaths = filter.maxJoinedPaths();
        builder.filter.maxToManyPaths = filter.maxToManyPaths();
        builder.filter.allowToManyFiltering = filter.allowToManyFiltering();
        builder.filter.requireDistinctForToMany = filter.requireDistinctForToMany();
        builder.filter.text.maxPatternLength = filter.text().maxPatternLength();
        builder.filter.text.minLiteralLength = filter.text().minLiteralLength();
        builder.filter.text.allowLeadingWildcard = filter.text().allowLeadingWildcard();
        builder.filter.text.allowTrailingWildcard = filter.text().allowTrailingWildcard();
        builder.filter.text.allowContains = filter.text().allowContains();
        builder.filter.text.maxWildcards = filter.text().maxWildcards();
        builder.filter.text.allowIgnoreCase = filter.text().allowIgnoreCase();
        builder.paging.minPage = paging.minPage();
        builder.paging.maxPage = paging.maxPage();
        builder.paging.minSize = paging.minSize();
        builder.paging.maxSize = paging.maxSize();
        builder.paging.maxOffset = paging.maxOffset();
        builder.paging.allowUnpaged = paging.allowUnpaged();
        builder.paging.defaultUnpagedSize = paging.defaultUnpagedSize();
        builder.paging.maxUnpagedSize = paging.maxUnpagedSize();
        builder.paging.page.allowToManyCount = paging.page().allowToManyCount();
        builder.paging.page.maxToManyPaths = paging.page().maxToManyPaths();
        builder.paging.page.allowDistinctCount = paging.page().allowDistinctCount();
        builder.paging.page.maxJoinedPaths = paging.page().maxJoinedPaths();
        builder.paging.slice.enabled = paging.slice().enabled();
        builder.paging.slice.preferForToMany = paging.slice().preferForToMany();
        builder.paging.slice.maxSize = paging.slice().maxSize();
        builder.sorting.maxOrders = sorting.maxOrders();
        builder.sorting.allowRelationSorting = sorting.allowRelationSorting();
        builder.sorting.maxRelationOrders = sorting.maxRelationOrders();
        builder.sorting.allowIgnoreCase = sorting.allowIgnoreCase();
        builder.sorting.allowNullHandling = sorting.allowNullHandling();
        builder.sorting.maxJoinedPaths = sorting.maxJoinedPaths();
        builder.sorting.disallowToManySorting = sorting.disallowToManySorting();
        builder.query.enabled = query.enabled();
        builder.query.minLength = query.minLength();
        builder.query.maxLength = query.maxLength();
        builder.query.requireValidator = query.requireValidator();
        builder.query.allowWithToManyFilter = query.allowWithToManyFilter();
        builder.query.allowWithRelationSort = query.allowWithRelationSort();
        builder.query.allowWithUnpaged = query.allowWithUnpaged();
        builder.paths.maxDepth = paths.maxDepth();
        return builder;
    }

    /**
     * Returns syntactic RSQL limits.
     *
     * @return RSQL policy
     */
    public Rsql rsql() {
        return rsql;
    }

    /**
     * Returns semantic filtering limits.
     *
     * @return filtering policy
     */
    public Filter filter() {
        return filter;
    }

    /**
     * Returns paging and page/slice limits.
     *
     * @return paging policy
     */
    public Paging paging() {
        return paging;
    }

    /**
     * Returns sorting limits.
     *
     * @return sorting policy
     */
    public Sorting sorting() {
        return sorting;
    }

    /**
     * Returns free-text query limits.
     *
     * @return query policy
     */
    public Query query() {
        return query;
    }

    /**
     * Returns definition path limits.
     *
     * @return path policy
     */
    public Paths paths() {
        return paths;
    }

    /** Builder for all policy groups. */
    public static final class Builder {
        private final List<Consumer<Builder>> overrides = new ArrayList<>();
        private final Rsql.Builder rsql;
        private final Filter.Builder filter;
        private final Paging.Builder paging;
        private final Sorting.Builder sorting;
        private final Query.Builder query;
        private final Paths.Builder paths;

        private Builder() {
            rsql = Rsql.builder(customizer -> overrides.add(builder -> builder.rsql(customizer)));
            filter = Filter.builder(customizer -> overrides.add(builder -> builder.filter(customizer)));
            paging = Paging.builder(customizer -> overrides.add(builder -> builder.paging(customizer)));
            sorting = Sorting.builder(customizer -> overrides.add(builder -> builder.sorting(customizer)));
            query = Query.builder(customizer -> overrides.add(builder -> builder.query(customizer)));
            paths = Paths.builder(customizer -> overrides.add(builder -> builder.paths(customizer)));
        }

        /**
         * Customizes syntactic RSQL limits.
         *
         * @param customizer RSQL policy customizer
         * @return this builder
         */
        public Builder rsql(Consumer<Rsql.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(rsql);
            return this;
        }

        /**
         * Customizes filtering limits.
         *
         * @param customizer filtering policy customizer
         * @return this builder
         */
        public Builder filter(Consumer<Filter.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(filter);
            return this;
        }

        /**
         * Customizes paging limits.
         *
         * @param customizer paging policy customizer
         * @return this builder
         */
        public Builder paging(Consumer<Paging.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(paging);
            return this;
        }

        /**
         * Customizes sorting limits.
         *
         * @param customizer sorting policy customizer
         * @return this builder
         */
        public Builder sorting(Consumer<Sorting.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(sorting);
            return this;
        }

        /**
         * Customizes free-text query limits.
         *
         * @param customizer query policy customizer
         * @return this builder
         */
        public Builder query(Consumer<Query.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(query);
            return this;
        }

        /**
         * Customizes definition path limits.
         *
         * @param customizer path policy customizer
         * @return this builder
         */
        public Builder paths(Consumer<Paths.Builder> customizer) {
            Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
            customizer.accept(paths);
            return this;
        }

        /**
         * Builds an immutable policy.
         *
         * @return validated policy
         */
        public SearchPolicy build() {
            return new SearchPolicy(
                    rsql.build(),
                    filter.build(),
                    paging.build(),
                    sorting.build(),
                    query.build(),
                    paths.build());
        }

        /**
         * Captures only setters invoked on this builder as a reusable overlay.
         *
         * @return immutable policy overlay
         */
        public UnaryOperator<SearchPolicy> buildOverlay() {
            List<Consumer<Builder>> snapshot = List.copyOf(overrides);
            return policy -> {
                Objects.requireNonNull(policy, "policy must not be null");
                Builder builder = policy.toBuilder();
                snapshot.forEach(override -> override.accept(builder));
                return builder.build();
            };
        }
    }

    /**
     * Syntactic parser limits applied before semantic validation.
     *
     * @param maxLength maximum filter length
     * @param maxParenthesesDepth maximum raw parenthesis nesting
     * @param maxNodes maximum AST nodes
     * @param maxDepth maximum AST depth
     * @param maxLogicalChildren maximum children of one logical node
     */
    public record Rsql(
            int maxLength,
            int maxParenthesesDepth,
            int maxNodes,
            int maxDepth,
            int maxLogicalChildren) {
        /** Validates positive syntactic limits. */
        public Rsql {
            requirePositive(maxLength, "rsql.maxLength");
            requirePositive(maxParenthesesDepth, "rsql.maxParenthesesDepth");
            requirePositive(maxNodes, "rsql.maxNodes");
            requirePositive(maxDepth, "rsql.maxDepth");
            requirePositive(maxLogicalChildren, "rsql.maxLogicalChildren");
        }

        /**
         * Creates an RSQL policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for syntactic RSQL limits. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private int maxLength = 4096;
            private int maxParenthesesDepth = 8;
            private int maxNodes = 48;
            private int maxDepth = 8;
            private int maxLogicalChildren = 16;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
            }

            /**
             * Sets maximum filter length.
             *
             * @param maxLength positive character limit
             * @return this builder
             */
            public Builder maxLength(int maxLength) {
                this.maxLength = maxLength;
                overrideRecorder.accept(builder -> builder.maxLength(maxLength));
                return this;
            }

            /**
             * Sets maximum raw parenthesis nesting.
             *
             * @param maxParenthesesDepth positive nesting limit
             * @return this builder
             */
            public Builder maxParenthesesDepth(int maxParenthesesDepth) {
                this.maxParenthesesDepth = maxParenthesesDepth;
                overrideRecorder.accept(builder -> builder.maxParenthesesDepth(maxParenthesesDepth));
                return this;
            }

            /**
             * Sets maximum AST nodes.
             *
             * @param maxNodes positive node limit
             * @return this builder
             */
            public Builder maxNodes(int maxNodes) {
                this.maxNodes = maxNodes;
                overrideRecorder.accept(builder -> builder.maxNodes(maxNodes));
                return this;
            }

            /**
             * Sets maximum AST depth.
             *
             * @param maxDepth positive depth limit
             * @return this builder
             */
            public Builder maxDepth(int maxDepth) {
                this.maxDepth = maxDepth;
                overrideRecorder.accept(builder -> builder.maxDepth(maxDepth));
                return this;
            }

            /**
             * Sets maximum logical fan-out.
             *
             * @param maxLogicalChildren positive child limit
             * @return this builder
             */
            public Builder maxLogicalChildren(int maxLogicalChildren) {
                this.maxLogicalChildren = maxLogicalChildren;
                overrideRecorder.accept(builder -> builder.maxLogicalChildren(maxLogicalChildren));
                return this;
            }

            /**
             * Builds RSQL limits.
             *
             * @return validated RSQL policy
             */
            public Rsql build() {
                return new Rsql(
                        maxLength,
                        maxParenthesesDepth,
                        maxNodes,
                        maxDepth,
                        maxLogicalChildren);
            }
        }
    }

    /**
     * Semantic filtering and topology limits.
     *
     * @param maxComparisons maximum comparisons in one filter
     * @param maxComparisonsPerSelector maximum comparisons for one selector
     * @param maxArgumentsPerComparison maximum arguments in one comparison
     * @param maxArgumentsTotal maximum arguments in the complete filter
     * @param maxArgumentLength maximum raw argument length
     * @param maxInValues maximum values accepted by {@code IN}
     * @param maxNotInValues maximum values accepted by {@code NOT IN}
     * @param maxBetweenRanges maximum range comparisons
     * @param maxNegatedComparisons maximum negated comparisons
     * @param maxOrBranches maximum accumulated OR branches
     * @param maxOrSelectors maximum selectors in an OR expression
     * @param maxOrJoinRoots maximum relation roots in an OR expression
     * @param maxHeterogeneousOrBranches maximum branches when OR selectors differ
     * @param maxJoinedPaths maximum distinct joined paths
     * @param maxToManyPaths maximum distinct to-many paths
     * @param allowToManyFiltering whether to-many filtering is allowed
     * @param requireDistinctForToMany whether to-many filters must request distinct
     * @param text text-pattern limits
     */
    public record Filter(
            int maxComparisons,
            int maxComparisonsPerSelector,
            int maxArgumentsPerComparison,
            int maxArgumentsTotal,
            int maxArgumentLength,
            int maxInValues,
            int maxNotInValues,
            int maxBetweenRanges,
            int maxNegatedComparisons,
            int maxOrBranches,
            int maxOrSelectors,
            int maxOrJoinRoots,
            int maxHeterogeneousOrBranches,
            int maxJoinedPaths,
            int maxToManyPaths,
            boolean allowToManyFiltering,
            boolean requireDistinctForToMany,
            Text text) {
        /** Validates filter limits. */
        public Filter {
            requirePositive(maxComparisons, "filter.maxComparisons");
            requirePositive(maxComparisonsPerSelector, "filter.maxComparisonsPerSelector");
            requirePositive(maxArgumentsPerComparison, "filter.maxArgumentsPerComparison");
            requirePositive(maxArgumentsTotal, "filter.maxArgumentsTotal");
            requirePositive(maxArgumentLength, "filter.maxArgumentLength");
            requirePositive(maxInValues, "filter.maxInValues");
            requirePositive(maxNotInValues, "filter.maxNotInValues");
            requireNonNegative(maxBetweenRanges, "filter.maxBetweenRanges");
            requireNonNegative(maxNegatedComparisons, "filter.maxNegatedComparisons");
            requirePositive(maxOrBranches, "filter.maxOrBranches");
            requirePositive(maxOrSelectors, "filter.maxOrSelectors");
            requireNonNegative(maxOrJoinRoots, "filter.maxOrJoinRoots");
            requireNonNegative(maxHeterogeneousOrBranches, "filter.maxHeterogeneousOrBranches");
            requireNonNegative(maxJoinedPaths, "filter.maxJoinedPaths");
            requireNonNegative(maxToManyPaths, "filter.maxToManyPaths");
            Objects.requireNonNull(text, "filter.text must not be null");
        }

        /**
         * Creates a filtering policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for semantic filtering limits. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private int maxComparisons = 24;
            private int maxComparisonsPerSelector = 8;
            private int maxArgumentsPerComparison = 50;
            private int maxArgumentsTotal = 75;
            private int maxArgumentLength = 256;
            private int maxInValues = 50;
            private int maxNotInValues = 25;
            private int maxBetweenRanges = 12;
            private int maxNegatedComparisons = 6;
            private int maxOrBranches = 16;
            private int maxOrSelectors = 8;
            private int maxOrJoinRoots = 2;
            private int maxHeterogeneousOrBranches = 8;
            private int maxJoinedPaths = 4;
            private int maxToManyPaths = 1;
            private boolean allowToManyFiltering = true;
            private boolean requireDistinctForToMany = true;
            private final Text.Builder text;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
                text = Text.builder(customizer ->
                        overrideRecorder.accept(builder -> builder.text(customizer)));
            }

            /**
             * Sets maximum comparisons.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxComparisons(int value) {
                maxComparisons = value;
                overrideRecorder.accept(builder -> builder.maxComparisons(value));
                return this;
            }

            /**
             * Sets maximum comparisons per selector.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxComparisonsPerSelector(int value) {
                maxComparisonsPerSelector = value;
                overrideRecorder.accept(builder -> builder.maxComparisonsPerSelector(value));
                return this;
            }

            /**
             * Sets maximum arguments per comparison.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxArgumentsPerComparison(int value) {
                maxArgumentsPerComparison = value;
                overrideRecorder.accept(builder -> builder.maxArgumentsPerComparison(value));
                return this;
            }

            /**
             * Sets maximum total arguments.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxArgumentsTotal(int value) {
                maxArgumentsTotal = value;
                overrideRecorder.accept(builder -> builder.maxArgumentsTotal(value));
                return this;
            }

            /**
             * Sets maximum raw argument length.
             *
             * @param value positive character limit
             * @return this builder
             */
            public Builder maxArgumentLength(int value) {
                maxArgumentLength = value;
                overrideRecorder.accept(builder -> builder.maxArgumentLength(value));
                return this;
            }

            /**
             * Sets maximum {@code IN} values.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxInValues(int value) {
                maxInValues = value;
                overrideRecorder.accept(builder -> builder.maxInValues(value));
                return this;
            }

            /**
             * Sets maximum {@code NOT IN} values.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxNotInValues(int value) {
                maxNotInValues = value;
                overrideRecorder.accept(builder -> builder.maxNotInValues(value));
                return this;
            }

            /**
             * Sets maximum range comparisons.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxBetweenRanges(int value) {
                maxBetweenRanges = value;
                overrideRecorder.accept(builder -> builder.maxBetweenRanges(value));
                return this;
            }

            /**
             * Sets maximum negated comparisons.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxNegatedComparisons(int value) {
                maxNegatedComparisons = value;
                overrideRecorder.accept(builder -> builder.maxNegatedComparisons(value));
                return this;
            }

            /**
             * Sets maximum OR branches.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxOrBranches(int value) {
                maxOrBranches = value;
                overrideRecorder.accept(builder -> builder.maxOrBranches(value));
                return this;
            }

            /**
             * Sets maximum selectors in OR expressions.
             *
             * @param value positive limit
             * @return this builder
             */
            public Builder maxOrSelectors(int value) {
                maxOrSelectors = value;
                overrideRecorder.accept(builder -> builder.maxOrSelectors(value));
                return this;
            }

            /**
             * Sets maximum relation roots in OR expressions.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxOrJoinRoots(int value) {
                maxOrJoinRoots = value;
                overrideRecorder.accept(builder -> builder.maxOrJoinRoots(value));
                return this;
            }

            /**
             * Sets maximum heterogeneous OR branches.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxHeterogeneousOrBranches(int value) {
                maxHeterogeneousOrBranches = value;
                overrideRecorder.accept(builder -> builder.maxHeterogeneousOrBranches(value));
                return this;
            }

            /**
             * Sets maximum joined filtering paths.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxJoinedPaths(int value) {
                maxJoinedPaths = value;
                overrideRecorder.accept(builder -> builder.maxJoinedPaths(value));
                return this;
            }

            /**
             * Sets maximum to-many filtering paths.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxToManyPaths(int value) {
                maxToManyPaths = value;
                overrideRecorder.accept(builder -> builder.maxToManyPaths(value));
                return this;
            }

            /**
             * Enables or disables to-many filtering.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowToManyFiltering(boolean value) {
                allowToManyFiltering = value;
                overrideRecorder.accept(builder -> builder.allowToManyFiltering(value));
                return this;
            }

            /**
             * Requires distinct for to-many filtering.
             *
             * @param value required state
             * @return this builder
             */
            public Builder requireDistinctForToMany(boolean value) {
                requireDistinctForToMany = value;
                overrideRecorder.accept(builder -> builder.requireDistinctForToMany(value));
                return this;
            }

            /**
             * Customizes text-pattern limits.
             *
             * @param customizer text policy customizer
             * @return this builder
             */
            public Builder text(Consumer<Text.Builder> customizer) {
                Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
                customizer.accept(text);
                return this;
            }

            /**
             * Builds filtering limits.
             *
             * @return validated filtering policy
             */
            public Filter build() {
                return new Filter(
                        maxComparisons,
                        maxComparisonsPerSelector,
                        maxArgumentsPerComparison,
                        maxArgumentsTotal,
                        maxArgumentLength,
                        maxInValues,
                        maxNotInValues,
                        maxBetweenRanges,
                        maxNegatedComparisons,
                        maxOrBranches,
                        maxOrSelectors,
                        maxOrJoinRoots,
                        maxHeterogeneousOrBranches,
                        maxJoinedPaths,
                        maxToManyPaths,
                        allowToManyFiltering,
                        requireDistinctForToMany,
                        text.build());
            }
        }

        /**
         * Limits for text-pattern and case-insensitive text operators.
         *
         * @param maxPatternLength maximum raw pattern length
         * @param minLiteralLength minimum non-wildcard characters
         * @param allowLeadingWildcard whether leading wildcards are allowed
         * @param allowTrailingWildcard whether trailing wildcards are allowed
         * @param allowContains whether leading and trailing wildcards may combine
         * @param maxWildcards maximum unescaped wildcard count
         * @param allowIgnoreCase whether case-insensitive operators are allowed
         */
        public record Text(
                int maxPatternLength,
                int minLiteralLength,
                boolean allowLeadingWildcard,
                boolean allowTrailingWildcard,
                boolean allowContains,
                int maxWildcards,
                boolean allowIgnoreCase) {
            /** Validates text-pattern limits. */
            public Text {
                requirePositive(maxPatternLength, "filter.text.maxPatternLength");
                requireNonNegative(minLiteralLength, "filter.text.minLiteralLength");
                requireNonNegative(maxWildcards, "filter.text.maxWildcards");
            }

            /**
             * Creates a text-pattern policy builder.
             *
             * @return new builder
             */
            public static Builder builder() {
                return builder(ignored -> {});
            }

            private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
                return new Builder(overrideRecorder);
            }

            /** Builder for text-pattern limits. */
            public static final class Builder {
                private final Consumer<Consumer<Builder>> overrideRecorder;
                private int maxPatternLength = 128;
                private int minLiteralLength = 3;
                private boolean allowLeadingWildcard;
                private boolean allowTrailingWildcard = true;
                private boolean allowContains;
                private int maxWildcards = 1;
                private boolean allowIgnoreCase = true;

                private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                    this.overrideRecorder = overrideRecorder;
                }

                /**
                 * Sets maximum pattern length.
                 *
                 * @param value positive character limit
                 * @return this builder
                 */
                public Builder maxPatternLength(int value) {
                    maxPatternLength = value;
                    overrideRecorder.accept(builder -> builder.maxPatternLength(value));
                    return this;
                }

                /**
                 * Sets minimum literal characters.
                 *
                 * @param value non-negative minimum
                 * @return this builder
                 */
                public Builder minLiteralLength(int value) {
                    minLiteralLength = value;
                    overrideRecorder.accept(builder -> builder.minLiteralLength(value));
                    return this;
                }

                /**
                 * Enables leading wildcards.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowLeadingWildcard(boolean value) {
                    allowLeadingWildcard = value;
                    overrideRecorder.accept(builder -> builder.allowLeadingWildcard(value));
                    return this;
                }

                /**
                 * Enables trailing wildcards.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowTrailingWildcard(boolean value) {
                    allowTrailingWildcard = value;
                    overrideRecorder.accept(builder -> builder.allowTrailingWildcard(value));
                    return this;
                }

                /**
                 * Enables contains-style patterns.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowContains(boolean value) {
                    allowContains = value;
                    overrideRecorder.accept(builder -> builder.allowContains(value));
                    return this;
                }

                /**
                 * Sets maximum wildcard count.
                 *
                 * @param value non-negative limit
                 * @return this builder
                 */
                public Builder maxWildcards(int value) {
                    maxWildcards = value;
                    overrideRecorder.accept(builder -> builder.maxWildcards(value));
                    return this;
                }

                /**
                 * Enables case-insensitive text operators.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowIgnoreCase(boolean value) {
                    allowIgnoreCase = value;
                    overrideRecorder.accept(builder -> builder.allowIgnoreCase(value));
                    return this;
                }

                /**
                 * Builds text-pattern limits.
                 *
                 * @return validated text-pattern policy
                 */
                public Text build() {
                    return new Text(
                            maxPatternLength,
                            minLiteralLength,
                            allowLeadingWildcard,
                            allowTrailingWildcard,
                            allowContains,
                            maxWildcards,
                            allowIgnoreCase);
                }
            }
        }
    }

    /**
     * Paging limits shared by page and slice requests.
     *
     * @param minPage minimum zero-based page index
     * @param maxPage maximum zero-based page index
     * @param minSize minimum requested page size
     * @param maxSize maximum requested page size
     * @param maxOffset maximum accepted row offset
     * @param allowUnpaged whether unpaged requests are accepted and converted to a bounded first page
     * @param defaultUnpagedSize page size used for converted unpaged requests
     * @param maxUnpagedSize maximum configured size accepted for converted unpaged requests
     * @param page count-query limits
     * @param slice slice-query limits
     */
    public record Paging(
            int minPage,
            int maxPage,
            int minSize,
            int maxSize,
            long maxOffset,
            boolean allowUnpaged,
            int defaultUnpagedSize,
            int maxUnpagedSize,
            Page page,
            Slice slice) {
        /** Validates paging limits and their ordering constraints. */
        public Paging {
            requireNonNegative(minPage, "paging.minPage");
            requireNonNegative(maxPage, "paging.maxPage");
            requirePositive(minSize, "paging.minSize");
            requirePositive(maxSize, "paging.maxSize");
            requireNonNegative(maxOffset, "paging.maxOffset");
            requirePositive(defaultUnpagedSize, "paging.defaultUnpagedSize");
            requirePositive(maxUnpagedSize, "paging.maxUnpagedSize");
            Objects.requireNonNull(page, "paging.page must not be null");
            Objects.requireNonNull(slice, "paging.slice must not be null");
            if (maxPage < minPage) {
                throw new IllegalArgumentException("paging.maxPage must be greater than or equal to paging.minPage");
            }
            if (maxSize < minSize) {
                throw new IllegalArgumentException("paging.maxSize must be greater than or equal to paging.minSize");
            }
            if (defaultUnpagedSize > maxUnpagedSize) {
                throw new IllegalArgumentException(
                        "paging.defaultUnpagedSize must be less than or equal to paging.maxUnpagedSize");
            }
        }

        /**
         * Creates a paging policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for paging limits. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private int minPage = 0;
            private int maxPage = 100;
            private int minSize = 1;
            private int maxSize = 100;
            private long maxOffset = 5_000;
            private boolean allowUnpaged;
            private int defaultUnpagedSize = 40;
            private int maxUnpagedSize = 100;
            private final Page.Builder page;
            private final Slice.Builder slice;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
                page = Page.builder(customizer ->
                        overrideRecorder.accept(builder -> builder.page(customizer)));
                slice = Slice.builder(customizer ->
                        overrideRecorder.accept(builder -> builder.slice(customizer)));
            }

            /**
             * Sets the minimum page index.
             *
             * @param minPage non-negative minimum
             * @return this builder
             */
            public Builder minPage(int minPage) {
                this.minPage = minPage;
                overrideRecorder.accept(builder -> builder.minPage(minPage));
                return this;
            }

            /**
             * Sets the maximum page index.
             *
             * @param maxPage non-negative maximum
             * @return this builder
             */
            public Builder maxPage(int maxPage) {
                this.maxPage = maxPage;
                overrideRecorder.accept(builder -> builder.maxPage(maxPage));
                return this;
            }

            /**
             * Sets the minimum page size.
             *
             * @param minSize positive minimum
             * @return this builder
             */
            public Builder minSize(int minSize) {
                this.minSize = minSize;
                overrideRecorder.accept(builder -> builder.minSize(minSize));
                return this;
            }

            /**
             * Sets the maximum page size.
             *
             * @param maxSize positive maximum
             * @return this builder
             */
            public Builder maxSize(int maxSize) {
                this.maxSize = maxSize;
                overrideRecorder.accept(builder -> builder.maxSize(maxSize));
                return this;
            }

            /**
             * Sets the maximum row offset.
             *
             * @param maxOffset non-negative maximum
             * @return this builder
             */
            public Builder maxOffset(long maxOffset) {
                this.maxOffset = maxOffset;
                overrideRecorder.accept(builder -> builder.maxOffset(maxOffset));
                return this;
            }

            /**
             * Enables or disables bounded handling for unpaged requests.
             *
             * @param allowUnpaged whether unpaged requests are accepted and converted to a bounded first page
             * @return this builder
             */
            public Builder allowUnpaged(boolean allowUnpaged) {
                this.allowUnpaged = allowUnpaged;
                overrideRecorder.accept(builder -> builder.allowUnpaged(allowUnpaged));
                return this;
            }

            /**
             * Sets the page size used when an accepted unpaged request is converted to a bounded first page.
             *
             * @param defaultUnpagedSize positive page size
             * @return this builder
             */
            public Builder defaultUnpagedSize(int defaultUnpagedSize) {
                this.defaultUnpagedSize = defaultUnpagedSize;
                overrideRecorder.accept(builder -> builder.defaultUnpagedSize(defaultUnpagedSize));
                return this;
            }

            /**
             * Sets the page size used when an accepted unpaged request is converted to a bounded first page.
             *
             * @param unpagedSize positive page size
             * @return this builder
             */
            public Builder unpagedSize(int unpagedSize) {
                return defaultUnpagedSize(unpagedSize);
            }

            /**
             * Sets the maximum accepted unpaged size.
             *
             * @param maxUnpagedSize positive maximum
             * @return this builder
             */
            public Builder maxUnpagedSize(int maxUnpagedSize) {
                this.maxUnpagedSize = maxUnpagedSize;
                overrideRecorder.accept(builder -> builder.maxUnpagedSize(maxUnpagedSize));
                return this;
            }

            /**
             * Customizes count-query limits.
             *
             * @param customizer page policy customizer
             * @return this builder
             */
            public Builder page(Consumer<Page.Builder> customizer) {
                Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
                customizer.accept(page);
                return this;
            }

            /**
             * Customizes slice-query limits.
             *
             * @param customizer slice policy customizer
             * @return this builder
             */
            public Builder slice(Consumer<Slice.Builder> customizer) {
                Objects.requireNonNull(customizer, CUSTOMIZER_MUST_NOT_BE_NULL);
                customizer.accept(slice);
                return this;
            }

            /**
             * Builds paging limits.
             *
             * @return validated paging policy
             */
            public Paging build() {
                return new Paging(
                        minPage,
                        maxPage,
                        minSize,
                        maxSize,
                        maxOffset,
                        allowUnpaged,
                        defaultUnpagedSize,
                        maxUnpagedSize,
                        page.build(),
                        slice.build());
            }
        }

        /**
         * Limits for page requests that require a count query.
         *
         * @param allowToManyCount whether count queries may traverse to-many paths
         * @param maxToManyPaths maximum to-many paths in a count query
         * @param allowDistinctCount whether distinct count queries are allowed
         * @param maxJoinedPaths maximum joined paths in a count query
         */
        public record Page(
                boolean allowToManyCount,
                int maxToManyPaths,
                boolean allowDistinctCount,
                int maxJoinedPaths) {
            /** Validates count-query limits. */
            public Page {
                requireNonNegative(maxToManyPaths, "paging.page.maxToManyPaths");
                requireNonNegative(maxJoinedPaths, "paging.page.maxJoinedPaths");
            }

            /**
             * Creates a page policy builder.
             *
             * @return new builder
             */
            public static Builder builder() {
                return builder(ignored -> {});
            }

            private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
                return new Builder(overrideRecorder);
            }

            /** Builder for count-query limits. */
            public static final class Builder {
                private final Consumer<Consumer<Builder>> overrideRecorder;
                private boolean allowToManyCount = true;
                private int maxToManyPaths = 1;
                private boolean allowDistinctCount = true;
                private int maxJoinedPaths = 4;

                private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                    this.overrideRecorder = overrideRecorder;
                }

                /**
                 * Enables count queries across to-many paths.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowToManyCount(boolean value) {
                    allowToManyCount = value;
                    overrideRecorder.accept(builder -> builder.allowToManyCount(value));
                    return this;
                }

                /**
                 * Sets maximum to-many paths in a count query.
                 *
                 * @param value non-negative limit
                 * @return this builder
                 */
                public Builder maxToManyPaths(int value) {
                    maxToManyPaths = value;
                    overrideRecorder.accept(builder -> builder.maxToManyPaths(value));
                    return this;
                }

                /**
                 * Enables distinct count queries.
                 *
                 * @param value allowed state
                 * @return this builder
                 */
                public Builder allowDistinctCount(boolean value) {
                    allowDistinctCount = value;
                    overrideRecorder.accept(builder -> builder.allowDistinctCount(value));
                    return this;
                }

                /**
                 * Sets maximum joined paths in a count query.
                 *
                 * @param value non-negative limit
                 * @return this builder
                 */
                public Builder maxJoinedPaths(int value) {
                    maxJoinedPaths = value;
                    overrideRecorder.accept(builder -> builder.maxJoinedPaths(value));
                    return this;
                }

                /**
                 * Builds page-specific limits.
                 *
                 * @return validated page policy
                 */
                public Page build() {
                    return new Page(
                            allowToManyCount,
                            maxToManyPaths,
                            allowDistinctCount,
                            maxJoinedPaths);
                }
            }
        }

        /**
         * Limits for slice requests.
         *
         * @param enabled whether slice compilation is enabled
         * @param preferForToMany whether slices are preferred for to-many filtering
         * @param maxSize maximum slice size
         */
        public record Slice(boolean enabled, boolean preferForToMany, int maxSize) {
            /** Validates slice limits. */
            public Slice {
                requirePositive(maxSize, "paging.slice.maxSize");
            }

            /**
             * Creates a slice policy builder.
             *
             * @return new builder
             */
            public static Builder builder() {
                return builder(ignored -> {});
            }

            private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
                return new Builder(overrideRecorder);
            }

            /** Builder for slice limits. */
            public static final class Builder {
                private final Consumer<Consumer<Builder>> overrideRecorder;
                private boolean enabled = true;
                private boolean preferForToMany = true;
                private int maxSize = 100;

                private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                    this.overrideRecorder = overrideRecorder;
                }

                /**
                 * Enables or disables slice compilation.
                 *
                 * @param value enabled state
                 * @return this builder
                 */
                public Builder enabled(boolean value) {
                    enabled = value;
                    overrideRecorder.accept(builder -> builder.enabled(value));
                    return this;
                }

                /**
                 * Sets whether slices are preferred for to-many filtering.
                 *
                 * @param value preferred state
                 * @return this builder
                 */
                public Builder preferForToMany(boolean value) {
                    preferForToMany = value;
                    overrideRecorder.accept(builder -> builder.preferForToMany(value));
                    return this;
                }

                /**
                 * Sets the maximum slice size.
                 *
                 * @param value positive limit
                 * @return this builder
                 */
                public Builder maxSize(int value) {
                    maxSize = value;
                    overrideRecorder.accept(builder -> builder.maxSize(value));
                    return this;
                }

                /**
                 * Builds slice-specific limits.
                 *
                 * @return validated slice policy
                 */
                public Slice build() {
                    return new Slice(enabled, preferForToMany, maxSize);
                }
            }
        }
    }

    /**
     * Sorting limits.
     *
     * @param maxOrders maximum sort orders
     * @param allowRelationSorting whether relation paths may be sorted
     * @param maxRelationOrders maximum relation-path sort orders
     * @param allowIgnoreCase whether case-insensitive sorting is allowed
     * @param allowNullHandling whether explicit null handling is allowed
     * @param maxJoinedPaths maximum distinct joined paths used by sorting
     * @param disallowToManySorting whether sorting through to-many paths is rejected
     */
    public record Sorting(
            int maxOrders,
            boolean allowRelationSorting,
            int maxRelationOrders,
            boolean allowIgnoreCase,
            boolean allowNullHandling,
            int maxJoinedPaths,
            boolean disallowToManySorting) {
        /** Validates sorting limits. */
        public Sorting {
            requirePositive(maxOrders, "sorting.maxOrders");
            requireNonNegative(maxRelationOrders, "sorting.maxRelationOrders");
            requireNonNegative(maxJoinedPaths, "sorting.maxJoinedPaths");
        }

        /**
         * Creates a sorting policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for sorting limits. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private int maxOrders = 3;
            private boolean allowRelationSorting = true;
            private int maxRelationOrders = 1;
            private boolean allowIgnoreCase;
            private boolean allowNullHandling;
            private int maxJoinedPaths = 2;
            private boolean disallowToManySorting = true;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
            }

            /**
             * Sets maximum sort orders.
             *
             * @param maxOrders positive limit
             * @return this builder
             */
            public Builder maxOrders(int maxOrders) {
                this.maxOrders = maxOrders;
                overrideRecorder.accept(builder -> builder.maxOrders(maxOrders));
                return this;
            }

            /**
             * Enables sorting through relation paths.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowRelationSorting(boolean value) {
                allowRelationSorting = value;
                overrideRecorder.accept(builder -> builder.allowRelationSorting(value));
                return this;
            }

            /**
             * Sets maximum relation-path sort orders.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxRelationOrders(int value) {
                maxRelationOrders = value;
                overrideRecorder.accept(builder -> builder.maxRelationOrders(value));
                return this;
            }

            /**
             * Enables case-insensitive sort orders.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowIgnoreCase(boolean value) {
                allowIgnoreCase = value;
                overrideRecorder.accept(builder -> builder.allowIgnoreCase(value));
                return this;
            }

            /**
             * Enables explicit null handling.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowNullHandling(boolean value) {
                allowNullHandling = value;
                overrideRecorder.accept(builder -> builder.allowNullHandling(value));
                return this;
            }

            /**
             * Sets maximum joined paths used by sorting.
             *
             * @param value non-negative limit
             * @return this builder
             */
            public Builder maxJoinedPaths(int value) {
                maxJoinedPaths = value;
                overrideRecorder.accept(builder -> builder.maxJoinedPaths(value));
                return this;
            }

            /**
             * Rejects or allows sorting through to-many paths.
             *
             * @param value rejection state
             * @return this builder
             */
            public Builder disallowToManySorting(boolean value) {
                disallowToManySorting = value;
                overrideRecorder.accept(builder -> builder.disallowToManySorting(value));
                return this;
            }

            /**
             * Builds sorting limits.
             *
             * @return validated sorting policy
             */
            public Sorting build() {
                return new Sorting(
                        maxOrders,
                        allowRelationSorting,
                        maxRelationOrders,
                        allowIgnoreCase,
                        allowNullHandling,
                        maxJoinedPaths,
                        disallowToManySorting);
            }
        }
    }

    /**
     * Full-text query limits and interaction rules.
     *
     * @param enabled whether query compilation is enabled
     * @param minLength minimum query length
     * @param maxLength maximum query length
     * @param requireValidator whether a definition validator is required
     * @param allowWithToManyFilter whether a query may accompany to-many filtering
     * @param allowWithRelationSort whether a query may accompany relation sorting
     * @param allowWithUnpaged whether a query may accompany an unpaged request
     */
    public record Query(
            boolean enabled,
            int minLength,
            int maxLength,
            boolean requireValidator,
            boolean allowWithToManyFilter,
            boolean allowWithRelationSort,
            boolean allowWithUnpaged) {
        /** Validates query limits. */
        public Query {
            requireNonNegative(minLength, "query.minLength");
            requirePositive(maxLength, "query.maxLength");
            if (maxLength < minLength) {
                throw new IllegalArgumentException("query.maxLength must be greater than or equal to query.minLength");
            }
        }

        /**
         * Creates a query policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for query limits and interaction rules. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private boolean enabled = true;
            private int minLength = 0;
            private int maxLength = 256;
            private boolean requireValidator;
            private boolean allowWithToManyFilter = true;
            private boolean allowWithRelationSort = true;
            private boolean allowWithUnpaged = true;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
            }

            /**
             * Enables or disables full-text query compilation.
             *
             * @param value enabled state
             * @return this builder
             */
            public Builder enabled(boolean value) {
                enabled = value;
                overrideRecorder.accept(builder -> builder.enabled(value));
                return this;
            }

            /**
             * Sets minimum query length.
             *
             * @param value non-negative minimum
             * @return this builder
             */
            public Builder minLength(int value) {
                minLength = value;
                overrideRecorder.accept(builder -> builder.minLength(value));
                return this;
            }

            /**
             * Sets maximum query length.
             *
             * @param value positive maximum
             * @return this builder
             */
            public Builder maxLength(int value) {
                maxLength = value;
                overrideRecorder.accept(builder -> builder.maxLength(value));
                return this;
            }

            /**
             * Requires a definition validator when query text is used.
             *
             * @param value required state
             * @return this builder
             */
            public Builder requireValidator(boolean value) {
                requireValidator = value;
                overrideRecorder.accept(builder -> builder.requireValidator(value));
                return this;
            }

            /**
             * Allows queries together with to-many filtering.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowWithToManyFilter(boolean value) {
                allowWithToManyFilter = value;
                overrideRecorder.accept(builder -> builder.allowWithToManyFilter(value));
                return this;
            }

            /**
             * Allows queries together with relation sorting.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowWithRelationSort(boolean value) {
                allowWithRelationSort = value;
                overrideRecorder.accept(builder -> builder.allowWithRelationSort(value));
                return this;
            }

            /**
             * Allows queries together with unpaged requests.
             *
             * @param value allowed state
             * @return this builder
             */
            public Builder allowWithUnpaged(boolean value) {
                allowWithUnpaged = value;
                overrideRecorder.accept(builder -> builder.allowWithUnpaged(value));
                return this;
            }

            /**
             * Builds query limits.
             *
             * @return validated query policy
             */
            public Query build() {
                return new Query(
                        enabled,
                        minLength,
                        maxLength,
                        requireValidator,
                        allowWithToManyFilter,
                        allowWithRelationSort,
                        allowWithUnpaged);
            }
        }
    }

    /**
     * Limits for dotted property paths.
     *
     * @param maxDepth maximum number of path segments
     */
    public record Paths(int maxDepth) {
        /** Validates path limits. */
        public Paths {
            requirePositive(maxDepth, "paths.maxDepth");
        }

        /**
         * Creates a path policy builder.
         *
         * @return new builder
         */
        public static Builder builder() {
            return builder(ignored -> {});
        }

        private static Builder builder(Consumer<Consumer<Builder>> overrideRecorder) {
            return new Builder(overrideRecorder);
        }

        /** Builder for path limits. */
        public static final class Builder {
            private final Consumer<Consumer<Builder>> overrideRecorder;
            private int maxDepth = 3;

            private Builder(Consumer<Consumer<Builder>> overrideRecorder) {
                this.overrideRecorder = overrideRecorder;
            }

            /**
             * Sets maximum path depth.
             *
             * @param maxDepth positive segment limit
             * @return this builder
             */
            public Builder maxDepth(int maxDepth) {
                this.maxDepth = maxDepth;
                overrideRecorder.accept(builder -> builder.maxDepth(maxDepth));
                return this;
            }

            /**
             * Builds path limits.
             *
             * @return validated path policy
             */
            public Paths build() {
                return new Paths(maxDepth);
            }
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to zero");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to zero");
        }
    }
}
