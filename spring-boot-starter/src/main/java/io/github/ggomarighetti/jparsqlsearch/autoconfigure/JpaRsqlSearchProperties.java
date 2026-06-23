package io.github.ggomarighetti.jparsqlsearch.autoconfigure;

import io.github.ggomarighetti.jparsqlsearch.policy.SearchPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bindable limits and backend settings for the starter auto-configuration. */
@ConfigurationProperties(prefix = "jpa.rsql.search")
class JpaRsqlSearchProperties {
    /** RSQL parser and backend settings. */
    private final Rsql rsql = new Rsql();
    /** Semantic filter limits. */
    private final Filter filter = new Filter();
    /** Paging and slice limits. */
    private final Paging paging = new Paging();
    /** Sorting limits. */
    private final Sorting sorting = new Sorting();
    /** Free-text query limits. */
    private final Query query = new Query();
    /** Entity path limits. */
    private final Paths paths = new Paths();

    public SearchPolicy toPolicy() {
        return SearchPolicy.builder()
                .rsql(builder -> builder
                        .maxLength(rsql.maxLength)
                        .maxParenthesesDepth(rsql.maxParenthesesDepth)
                        .maxNodes(rsql.maxNodes)
                        .maxDepth(rsql.maxDepth)
                        .maxLogicalChildren(rsql.maxLogicalChildren))
                .filter(builder -> builder
                        .maxComparisons(filter.maxComparisons)
                        .maxComparisonsPerSelector(filter.maxComparisonsPerSelector)
                        .maxArgumentsPerComparison(filter.maxArgumentsPerComparison)
                        .maxArgumentsTotal(filter.maxArgumentsTotal)
                        .maxArgumentLength(filter.maxArgumentLength)
                        .maxInValues(filter.maxInValues)
                        .maxNotInValues(filter.maxNotInValues)
                        .maxBetweenRanges(filter.maxBetweenRanges)
                        .maxNegatedComparisons(filter.maxNegatedComparisons)
                        .maxOrBranches(filter.maxOrBranches)
                        .maxOrSelectors(filter.maxOrSelectors)
                        .maxOrJoinRoots(filter.maxOrJoinRoots)
                        .maxHeterogeneousOrBranches(filter.maxHeterogeneousOrBranches)
                        .maxJoinedPaths(filter.maxJoinedPaths)
                        .maxToManyPaths(filter.maxToManyPaths)
                        .allowToManyFiltering(filter.allowToManyFiltering)
                        .requireDistinctForToMany(filter.requireDistinctForToMany)
                        .text(text -> text
                                .maxPatternLength(filter.text.maxPatternLength)
                                .minLiteralLength(filter.text.minLiteralLength)
                                .allowLeadingWildcard(filter.text.allowLeadingWildcard)
                                .allowTrailingWildcard(filter.text.allowTrailingWildcard)
                                .allowContains(filter.text.allowContains)
                                .maxWildcards(filter.text.maxWildcards)
                                .allowIgnoreCase(filter.text.allowIgnoreCase)))
                .paging(builder -> builder
                        .minPage(paging.minPage)
                        .maxPage(paging.maxPage)
                        .minSize(paging.minSize)
                        .maxSize(paging.maxSize)
                        .maxOffset(paging.maxOffset)
                        .allowUnpaged(paging.allowUnpaged)
                        .defaultUnpagedSize(paging.defaultUnpagedSize)
                        .maxUnpagedSize(paging.maxUnpagedSize)
                        .page(page -> page
                                .allowToManyCount(paging.page.allowToManyCount)
                                .maxToManyPaths(paging.page.maxToManyPaths)
                                .allowDistinctCount(paging.page.allowDistinctCount)
                                .maxJoinedPaths(paging.page.maxJoinedPaths))
                        .slice(slice -> slice
                                .enabled(paging.slice.enabled)
                                .preferForToMany(paging.slice.preferForToMany)
                                .maxSize(paging.slice.maxSize)))
                .sorting(builder -> builder
                        .maxOrders(sorting.maxOrders)
                        .allowRelationSorting(sorting.allowRelationSorting)
                        .maxRelationOrders(sorting.maxRelationOrders)
                        .allowIgnoreCase(sorting.allowIgnoreCase)
                        .allowNullHandling(sorting.allowNullHandling)
                        .maxJoinedPaths(sorting.maxJoinedPaths)
                        .disallowToManySorting(sorting.disallowToManySorting))
                .query(builder -> builder
                        .enabled(query.enabled)
                        .minLength(query.minLength)
                        .maxLength(query.maxLength)
                        .requireValidator(query.requireValidator)
                        .allowWithToManyFilter(query.allowWithToManyFilter)
                        .allowWithRelationSort(query.allowWithRelationSort)
                        .allowWithUnpaged(query.allowWithUnpaged))
                .paths(builder -> builder.maxDepth(paths.maxDepth))
                .build();
    }

    public Rsql getRsql() {
        return rsql;
    }

    public Filter getFilter() {
        return filter;
    }

    public Paging getPaging() {
        return paging;
    }

    public Sorting getSorting() {
        return sorting;
    }

    public Query getQuery() {
        return query;
    }

    public Paths getPaths() {
        return paths;
    }

    /** RSQL parser and backend settings. */
    public static class Rsql {
        /**
         * Whether the built-in RSQL engine and backend auto-configuration are enabled.
         * When disabled, a SearchCompiler is auto-configured only if the application
         * provides its own SearchRsqlEngine bean.
         */
        private boolean enabled = true;
        /** Settings for the bundled Perplexhub JPA backend. */
        private final Perplexhub perplexhub = new Perplexhub();
        /** Maximum RSQL input length in characters. */
        private int maxLength = SearchPolicy.defaults().rsql().maxLength();
        /** Maximum raw parenthesis nesting depth. */
        private int maxParenthesesDepth = SearchPolicy.defaults().rsql().maxParenthesesDepth();
        /** Maximum nodes in the parsed RSQL tree. */
        private int maxNodes = SearchPolicy.defaults().rsql().maxNodes();
        /** Maximum depth of the parsed RSQL tree. */
        private int maxDepth = SearchPolicy.defaults().rsql().maxDepth();
        /** Maximum direct children of a logical RSQL node. */
        private int maxLogicalChildren = SearchPolicy.defaults().rsql().maxLogicalChildren();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Perplexhub getPerplexhub() {
            return perplexhub;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }

        public int getMaxParenthesesDepth() {
            return maxParenthesesDepth;
        }

        public void setMaxParenthesesDepth(int maxParenthesesDepth) {
            this.maxParenthesesDepth = maxParenthesesDepth;
        }

        public int getMaxNodes() {
            return maxNodes;
        }

        public void setMaxNodes(int maxNodes) {
            this.maxNodes = maxNodes;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxLogicalChildren() {
            return maxLogicalChildren;
        }

        public void setMaxLogicalChildren(int maxLogicalChildren) {
            this.maxLogicalChildren = maxLogicalChildren;
        }

        /** Settings for the bundled Perplexhub JPA backend. */
        public static class Perplexhub {
            /** Whether equality operators require exact backend equality semantics. */
            private boolean strictEquality = true;
            /** Optional escape character used by LIKE expressions. */
            private Character likeEscapeCharacter;

            public boolean isStrictEquality() {
                return strictEquality;
            }

            public void setStrictEquality(boolean strictEquality) {
                this.strictEquality = strictEquality;
            }

            public Character getLikeEscapeCharacter() {
                return likeEscapeCharacter;
            }

            public void setLikeEscapeCharacter(Character likeEscapeCharacter) {
                this.likeEscapeCharacter = likeEscapeCharacter;
            }
        }
    }

    /** Semantic filtering and relationship-topology limits. */
    public static class Filter {
        /** Text-pattern operator limits. */
        private final Text text = new Text();
        /** Maximum comparisons in one filter. */
        private int maxComparisons = SearchPolicy.defaults().filter().maxComparisons();
        /** Maximum comparisons targeting the same selector. */
        private int maxComparisonsPerSelector = SearchPolicy.defaults().filter().maxComparisonsPerSelector();
        /** Maximum arguments in one comparison. */
        private int maxArgumentsPerComparison = SearchPolicy.defaults().filter().maxArgumentsPerComparison();
        /** Maximum arguments across the complete filter. */
        private int maxArgumentsTotal = SearchPolicy.defaults().filter().maxArgumentsTotal();
        /** Maximum raw argument length in characters. */
        private int maxArgumentLength = SearchPolicy.defaults().filter().maxArgumentLength();
        /** Maximum values accepted by an IN comparison. */
        private int maxInValues = SearchPolicy.defaults().filter().maxInValues();
        /** Maximum values accepted by a NOT IN comparison. */
        private int maxNotInValues = SearchPolicy.defaults().filter().maxNotInValues();
        /** Maximum range comparisons. */
        private int maxBetweenRanges = SearchPolicy.defaults().filter().maxBetweenRanges();
        /** Maximum negated comparisons. */
        private int maxNegatedComparisons = SearchPolicy.defaults().filter().maxNegatedComparisons();
        /** Maximum accumulated OR branches. */
        private int maxOrBranches = SearchPolicy.defaults().filter().maxOrBranches();
        /** Maximum distinct selectors in an OR expression. */
        private int maxOrSelectors = SearchPolicy.defaults().filter().maxOrSelectors();
        /** Maximum relation roots in an OR expression. */
        private int maxOrJoinRoots = SearchPolicy.defaults().filter().maxOrJoinRoots();
        /** Maximum OR branches when selectors differ. */
        private int maxHeterogeneousOrBranches = SearchPolicy.defaults().filter().maxHeterogeneousOrBranches();
        /** Maximum distinct joined paths used by filtering. */
        private int maxJoinedPaths = SearchPolicy.defaults().filter().maxJoinedPaths();
        /** Maximum distinct to-many paths used by filtering. */
        private int maxToManyPaths = SearchPolicy.defaults().filter().maxToManyPaths();
        /** Whether filters may traverse to-many relationships. */
        private boolean allowToManyFiltering = SearchPolicy.defaults().filter().allowToManyFiltering();
        /** Whether to-many filters require a distinct query. */
        private boolean requireDistinctForToMany = SearchPolicy.defaults().filter().requireDistinctForToMany();

        public Text getText() {
            return text;
        }

        public int getMaxComparisons() {
            return maxComparisons;
        }

        public void setMaxComparisons(int value) {
            maxComparisons = value;
        }

        public int getMaxComparisonsPerSelector() {
            return maxComparisonsPerSelector;
        }

        public void setMaxComparisonsPerSelector(int value) {
            maxComparisonsPerSelector = value;
        }

        public int getMaxArgumentsPerComparison() {
            return maxArgumentsPerComparison;
        }

        public void setMaxArgumentsPerComparison(int value) {
            maxArgumentsPerComparison = value;
        }

        public int getMaxArgumentsTotal() {
            return maxArgumentsTotal;
        }

        public void setMaxArgumentsTotal(int value) {
            maxArgumentsTotal = value;
        }

        public int getMaxArgumentLength() {
            return maxArgumentLength;
        }

        public void setMaxArgumentLength(int value) {
            maxArgumentLength = value;
        }

        public int getMaxInValues() {
            return maxInValues;
        }

        public void setMaxInValues(int value) {
            maxInValues = value;
        }

        public int getMaxNotInValues() {
            return maxNotInValues;
        }

        public void setMaxNotInValues(int value) {
            maxNotInValues = value;
        }

        public int getMaxBetweenRanges() {
            return maxBetweenRanges;
        }

        public void setMaxBetweenRanges(int value) {
            maxBetweenRanges = value;
        }

        public int getMaxNegatedComparisons() {
            return maxNegatedComparisons;
        }

        public void setMaxNegatedComparisons(int value) {
            maxNegatedComparisons = value;
        }

        public int getMaxOrBranches() {
            return maxOrBranches;
        }

        public void setMaxOrBranches(int value) {
            maxOrBranches = value;
        }

        public int getMaxOrSelectors() {
            return maxOrSelectors;
        }

        public void setMaxOrSelectors(int value) {
            maxOrSelectors = value;
        }

        public int getMaxOrJoinRoots() {
            return maxOrJoinRoots;
        }

        public void setMaxOrJoinRoots(int value) {
            maxOrJoinRoots = value;
        }

        public int getMaxHeterogeneousOrBranches() {
            return maxHeterogeneousOrBranches;
        }

        public void setMaxHeterogeneousOrBranches(int value) {
            maxHeterogeneousOrBranches = value;
        }

        public int getMaxJoinedPaths() {
            return maxJoinedPaths;
        }

        public void setMaxJoinedPaths(int value) {
            maxJoinedPaths = value;
        }

        public int getMaxToManyPaths() {
            return maxToManyPaths;
        }

        public void setMaxToManyPaths(int value) {
            maxToManyPaths = value;
        }

        public boolean isAllowToManyFiltering() {
            return allowToManyFiltering;
        }

        public void setAllowToManyFiltering(boolean value) {
            allowToManyFiltering = value;
        }

        public boolean isRequireDistinctForToMany() {
            return requireDistinctForToMany;
        }

        public void setRequireDistinctForToMany(boolean value) {
            requireDistinctForToMany = value;
        }

        /** Text-pattern and case-insensitive text operator limits. */
        public static class Text {
            /** Maximum raw text pattern length in characters. */
            private int maxPatternLength = SearchPolicy.defaults().filter().text().maxPatternLength();
            /** Minimum number of non-wildcard characters in a text pattern. */
            private int minLiteralLength = SearchPolicy.defaults().filter().text().minLiteralLength();
            /** Whether text patterns may start with a wildcard. */
            private boolean allowLeadingWildcard = SearchPolicy.defaults().filter().text().allowLeadingWildcard();
            /** Whether text patterns may end with a wildcard. */
            private boolean allowTrailingWildcard = SearchPolicy.defaults().filter().text().allowTrailingWildcard();
            /** Whether one pattern may combine leading and trailing wildcards. */
            private boolean allowContains = SearchPolicy.defaults().filter().text().allowContains();
            /** Maximum unescaped wildcard count in a text pattern. */
            private int maxWildcards = SearchPolicy.defaults().filter().text().maxWildcards();
            /** Whether case-insensitive text operators are allowed. */
            private boolean allowIgnoreCase = SearchPolicy.defaults().filter().text().allowIgnoreCase();

            public int getMaxPatternLength() {
                return maxPatternLength;
            }

            public void setMaxPatternLength(int value) {
                maxPatternLength = value;
            }

            public int getMinLiteralLength() {
                return minLiteralLength;
            }

            public void setMinLiteralLength(int value) {
                minLiteralLength = value;
            }

            public boolean isAllowLeadingWildcard() {
                return allowLeadingWildcard;
            }

            public void setAllowLeadingWildcard(boolean value) {
                allowLeadingWildcard = value;
            }

            public boolean isAllowTrailingWildcard() {
                return allowTrailingWildcard;
            }

            public void setAllowTrailingWildcard(boolean value) {
                allowTrailingWildcard = value;
            }

            public boolean isAllowContains() {
                return allowContains;
            }

            public void setAllowContains(boolean value) {
                allowContains = value;
            }

            public int getMaxWildcards() {
                return maxWildcards;
            }

            public void setMaxWildcards(int value) {
                maxWildcards = value;
            }

            public boolean isAllowIgnoreCase() {
                return allowIgnoreCase;
            }

            public void setAllowIgnoreCase(boolean value) {
                allowIgnoreCase = value;
            }
        }
    }

    /** Paging, count-query and slice limits. */
    public static class Paging {
        /** Limits for page requests that execute a count query. */
        private final Page page = new Page();
        /** Limits for slice requests. */
        private final Slice slice = new Slice();
        /** Minimum zero-based page index. */
        private int minPage = SearchPolicy.defaults().paging().minPage();
        /** Maximum zero-based page index. */
        private int maxPage = SearchPolicy.defaults().paging().maxPage();
        /** Minimum requested page size. */
        private int minSize = SearchPolicy.defaults().paging().minSize();
        /** Maximum requested page size. */
        private int maxSize = SearchPolicy.defaults().paging().maxSize();
        /** Maximum accepted row offset. */
        private long maxOffset = SearchPolicy.defaults().paging().maxOffset();
        /** Whether unpaged requests are accepted and converted to a bounded first page. */
        private boolean allowUnpaged = SearchPolicy.defaults().paging().allowUnpaged();
        /** Page size used when an accepted unpaged request is converted to a bounded first page. */
        private int defaultUnpagedSize = SearchPolicy.defaults().paging().defaultUnpagedSize();
        /** Maximum configured size accepted for converted unpaged requests. */
        private int maxUnpagedSize = SearchPolicy.defaults().paging().maxUnpagedSize();

        public Page getPage() {
            return page;
        }

        public Slice getSlice() {
            return slice;
        }

        public int getMinPage() {
            return minPage;
        }

        public void setMinPage(int minPage) {
            this.minPage = minPage;
        }

        public int getMaxPage() {
            return maxPage;
        }

        public void setMaxPage(int maxPage) {
            this.maxPage = maxPage;
        }

        public int getMinSize() {
            return minSize;
        }

        public void setMinSize(int minSize) {
            this.minSize = minSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public long getMaxOffset() {
            return maxOffset;
        }

        public void setMaxOffset(long maxOffset) {
            this.maxOffset = maxOffset;
        }

        public boolean isAllowUnpaged() {
            return allowUnpaged;
        }

        public void setAllowUnpaged(boolean allowUnpaged) {
            this.allowUnpaged = allowUnpaged;
        }

        public int getDefaultUnpagedSize() {
            return defaultUnpagedSize;
        }

        public void setDefaultUnpagedSize(int defaultUnpagedSize) {
            this.defaultUnpagedSize = defaultUnpagedSize;
        }

        public int getMaxUnpagedSize() {
            return maxUnpagedSize;
        }

        public void setMaxUnpagedSize(int maxUnpagedSize) {
            this.maxUnpagedSize = maxUnpagedSize;
        }

        /** Limits for page requests that execute a count query. */
        public static class Page {
            /** Whether count queries may traverse to-many relationships. */
            private boolean allowToManyCount = SearchPolicy.defaults().paging().page().allowToManyCount();
            /** Maximum to-many paths in a count query. */
            private int maxToManyPaths = SearchPolicy.defaults().paging().page().maxToManyPaths();
            /** Whether distinct count queries are allowed. */
            private boolean allowDistinctCount = SearchPolicy.defaults().paging().page().allowDistinctCount();
            /** Maximum joined paths in a count query. */
            private int maxJoinedPaths = SearchPolicy.defaults().paging().page().maxJoinedPaths();

            public boolean isAllowToManyCount() {
                return allowToManyCount;
            }

            public void setAllowToManyCount(boolean value) {
                allowToManyCount = value;
            }

            public int getMaxToManyPaths() {
                return maxToManyPaths;
            }

            public void setMaxToManyPaths(int value) {
                maxToManyPaths = value;
            }

            public boolean isAllowDistinctCount() {
                return allowDistinctCount;
            }

            public void setAllowDistinctCount(boolean value) {
                allowDistinctCount = value;
            }

            public int getMaxJoinedPaths() {
                return maxJoinedPaths;
            }

            public void setMaxJoinedPaths(int value) {
                maxJoinedPaths = value;
            }
        }

        /** Limits for slice requests. */
        public static class Slice {
            /** Whether slice compilation is enabled. */
            private boolean enabled = SearchPolicy.defaults().paging().slice().enabled();
            /** Whether slices are preferred when filtering through to-many paths. */
            private boolean preferForToMany = SearchPolicy.defaults().paging().slice().preferForToMany();
            /** Maximum requested slice size. */
            private int maxSize = SearchPolicy.defaults().paging().slice().maxSize();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean value) {
                enabled = value;
            }

            public boolean isPreferForToMany() {
                return preferForToMany;
            }

            public void setPreferForToMany(boolean value) {
                preferForToMany = value;
            }

            public int getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(int value) {
                maxSize = value;
            }
        }
    }

    /** Sorting limits and relationship-topology rules. */
    public static class Sorting {
        /** Maximum sort orders in one request. */
        private int maxOrders = SearchPolicy.defaults().sorting().maxOrders();
        /** Whether sorting may traverse relation paths. */
        private boolean allowRelationSorting = SearchPolicy.defaults().sorting().allowRelationSorting();
        /** Maximum sort orders that traverse relation paths. */
        private int maxRelationOrders = SearchPolicy.defaults().sorting().maxRelationOrders();
        /** Whether case-insensitive sorting is allowed. */
        private boolean allowIgnoreCase = SearchPolicy.defaults().sorting().allowIgnoreCase();
        /** Whether explicit null handling is allowed. */
        private boolean allowNullHandling = SearchPolicy.defaults().sorting().allowNullHandling();
        /** Maximum distinct joined paths used by sorting. */
        private int maxJoinedPaths = SearchPolicy.defaults().sorting().maxJoinedPaths();
        /** Whether sorting through to-many relationships is rejected. */
        private boolean disallowToManySorting = SearchPolicy.defaults().sorting().disallowToManySorting();

        public int getMaxOrders() {
            return maxOrders;
        }

        public void setMaxOrders(int maxOrders) {
            this.maxOrders = maxOrders;
        }

        public boolean isAllowRelationSorting() {
            return allowRelationSorting;
        }

        public void setAllowRelationSorting(boolean value) {
            allowRelationSorting = value;
        }

        public int getMaxRelationOrders() {
            return maxRelationOrders;
        }

        public void setMaxRelationOrders(int value) {
            maxRelationOrders = value;
        }

        public boolean isAllowIgnoreCase() {
            return allowIgnoreCase;
        }

        public void setAllowIgnoreCase(boolean value) {
            allowIgnoreCase = value;
        }

        public boolean isAllowNullHandling() {
            return allowNullHandling;
        }

        public void setAllowNullHandling(boolean value) {
            allowNullHandling = value;
        }

        public int getMaxJoinedPaths() {
            return maxJoinedPaths;
        }

        public void setMaxJoinedPaths(int value) {
            maxJoinedPaths = value;
        }

        public boolean isDisallowToManySorting() {
            return disallowToManySorting;
        }

        public void setDisallowToManySorting(boolean value) {
            disallowToManySorting = value;
        }
    }

    /** Free-text query limits and interaction rules. */
    public static class Query {
        /** Whether free-text query compilation is enabled. */
        private boolean enabled = SearchPolicy.defaults().query().enabled();
        /** Minimum accepted query length in characters. */
        private int minLength = SearchPolicy.defaults().query().minLength();
        /** Maximum accepted query length in characters. */
        private int maxLength = SearchPolicy.defaults().query().maxLength();
        /** Whether query definitions must declare validation rules. */
        private boolean requireValidator = SearchPolicy.defaults().query().requireValidator();
        /** Whether a query may accompany to-many filtering. */
        private boolean allowWithToManyFilter = SearchPolicy.defaults().query().allowWithToManyFilter();
        /** Whether a query may accompany relation sorting. */
        private boolean allowWithRelationSort = SearchPolicy.defaults().query().allowWithRelationSort();
        /** Whether a query may accompany an unpaged request. */
        private boolean allowWithUnpaged = SearchPolicy.defaults().query().allowWithUnpaged();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int value) {
            minLength = value;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int value) {
            maxLength = value;
        }

        public boolean isRequireValidator() {
            return requireValidator;
        }

        public void setRequireValidator(boolean value) {
            requireValidator = value;
        }

        public boolean isAllowWithToManyFilter() {
            return allowWithToManyFilter;
        }

        public void setAllowWithToManyFilter(boolean value) {
            allowWithToManyFilter = value;
        }

        public boolean isAllowWithRelationSort() {
            return allowWithRelationSort;
        }

        public void setAllowWithRelationSort(boolean value) {
            allowWithRelationSort = value;
        }

        public boolean isAllowWithUnpaged() {
            return allowWithUnpaged;
        }

        public void setAllowWithUnpaged(boolean value) {
            allowWithUnpaged = value;
        }
    }

    /** Limits for dotted entity paths. */
    public static class Paths {
        /** Maximum number of path segments. */
        private int maxDepth = SearchPolicy.defaults().paths().maxDepth();

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }
    }

}
