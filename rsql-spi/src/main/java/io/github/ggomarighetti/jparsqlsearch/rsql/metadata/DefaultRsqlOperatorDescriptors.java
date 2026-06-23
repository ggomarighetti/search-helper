package io.github.ggomarighetti.jparsqlsearch.rsql.metadata;

import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperator;
import io.github.ggomarighetti.jparsqlsearch.rsql.operator.RsqlOperators;
import java.util.List;
import java.util.Set;

/** Built-in RSQL operator descriptors understood by the default engine configuration. */
public final class DefaultRsqlOperatorDescriptors {
    private static final Set<RsqlOperator> DEFAULT_OPERATORS = Set.of(
            RsqlOperators.EQUAL,
            RsqlOperators.NOT_EQUAL,
            RsqlOperators.GREATER_THAN,
            RsqlOperators.GREATER_THAN_OR_EQUAL,
            RsqlOperators.LESS_THAN,
            RsqlOperators.LESS_THAN_OR_EQUAL,
            RsqlOperators.IN,
            RsqlOperators.NOT_IN,
            RsqlOperators.IS_NULL,
            RsqlOperators.NOT_NULL,
            RsqlOperators.LIKE,
            RsqlOperators.NOT_LIKE,
            RsqlOperators.IGNORE_CASE,
            RsqlOperators.IGNORE_CASE_LIKE,
            RsqlOperators.IGNORE_CASE_NOT_LIKE,
            RsqlOperators.BETWEEN,
            RsqlOperators.NOT_BETWEEN);

    private DefaultRsqlOperatorDescriptors() {
    }

    /**
     * Returns every built-in descriptor.
     *
     * @return immutable descriptors for every built-in operator
     */
    public static List<RsqlOperatorDescriptor> all() {
        return List.of(
                descriptor(RsqlOperators.EQUAL, RsqlOperatorArity.exact(1), "=="),
                descriptor(RsqlOperators.NOT_EQUAL, RsqlOperatorArity.exact(1), "!="),
                descriptor(RsqlOperators.GREATER_THAN, RsqlOperatorArity.exact(1), "=gt=", ">"),
                descriptor(RsqlOperators.GREATER_THAN_OR_EQUAL, RsqlOperatorArity.exact(1), "=ge=", ">="),
                descriptor(RsqlOperators.LESS_THAN, RsqlOperatorArity.exact(1), "=lt=", "<"),
                descriptor(RsqlOperators.LESS_THAN_OR_EQUAL, RsqlOperatorArity.exact(1), "=le=", "<="),
                descriptor(RsqlOperators.IN, RsqlOperatorArity.atLeast(1), "=in="),
                descriptor(RsqlOperators.NOT_IN, RsqlOperatorArity.atLeast(1), "=out="),
                descriptor(RsqlOperators.IS_NULL, RsqlOperatorArity.between(0, 1), "=na=", "=isnull=", "=null="),
                descriptor(RsqlOperators.NOT_NULL, RsqlOperatorArity.between(0, 1), "=nn=", "=notnull=", "=isnotnull="),
                descriptor(RsqlOperators.LIKE, RsqlOperatorArity.exact(1), "=ke=", "=like="),
                descriptor(RsqlOperators.NOT_LIKE, RsqlOperatorArity.exact(1), "=nk=", "=notlike="),
                descriptor(RsqlOperators.IGNORE_CASE, RsqlOperatorArity.exact(1), "=ic=", "=icase="),
                descriptor(RsqlOperators.IGNORE_CASE_LIKE, RsqlOperatorArity.exact(1), "=ik=", "=ilike="),
                descriptor(RsqlOperators.IGNORE_CASE_NOT_LIKE, RsqlOperatorArity.exact(1), "=ni=", "=inotlike="),
                descriptor(RsqlOperators.BETWEEN, RsqlOperatorArity.exact(2), "=bt=", "=between="),
                descriptor(RsqlOperators.NOT_BETWEEN, RsqlOperatorArity.exact(2), "=nb=", "=notbetween="));
    }

    /**
     * Reports whether an operator belongs to the built-in set.
     *
     * @param operator operator to inspect
     * @return {@code true} for a built-in operator
     */
    public static boolean isDefault(RsqlOperator operator) {
        return DEFAULT_OPERATORS.contains(operator);
    }

    private static RsqlOperatorDescriptor descriptor(
            RsqlOperator operator,
            RsqlOperatorArity arity,
            String... symbols) {
        return RsqlOperatorDescriptor.builder(operator)
                .symbols(symbols)
                .arity(arity)
                .build();
    }
}
