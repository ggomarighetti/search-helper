package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import net.ttddyy.dsproxy.QueryInfo;

public final class PostgresQueryRecorder implements QueryExecutionListener {
    private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

    public <T> Capture<T> capture(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        if (SESSION.get() != null) {
            throw new IllegalStateException("A query capture is already active on this thread");
        }
        Session session = new Session();
        SESSION.set(session);
        try {
            return new Capture<>(operation.get(), List.copyOf(session.queries));
        } finally {
            SESSION.remove();
        }
    }

    public <T> T withoutRecording(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        Session previous = SESSION.get();
        SESSION.remove();
        try {
            return operation.get();
        } finally {
            if (previous != null) {
                SESSION.set(previous);
            }
        }
    }

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        // Intentionally empty: queries are recorded only after successful execution.
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        Session session = SESSION.get();
        if (session == null || executionInfo.getThrowable() != null) {
            return;
        }
        for (QueryInfo queryInfo : queryInfoList) {
            String sql = queryInfo.getQuery();
            if (!isApplicationSelect(sql)) {
                continue;
            }
            List<List<ParameterSetOperation>> parameterSets = queryInfo.getParametersList();
            List<ParameterSetOperation> parameters =
                    parameterSets.isEmpty() ? List.of() : copy(parameterSets.get(0));
            session.queries.add(new RecordedQuery(sql, parameters, classify(sql)));
        }
    }

    private static boolean isApplicationSelect(String sql) {
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.startsWith("select") && !normalized.startsWith("select pg_catalog");
    }

    private static QueryKind classify(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.contains("count(") ? QueryKind.COUNT : QueryKind.CONTENT;
    }

    private static List<ParameterSetOperation> copy(List<ParameterSetOperation> operations) {
        List<ParameterSetOperation> copied = new ArrayList<>(operations.size());
        for (ParameterSetOperation operation : operations) {
            Method method = operation.getMethod();
            Object[] args = operation.getArgs();
            copied.add(new ParameterSetOperation(method, args == null ? null : args.clone()));
        }
        return List.copyOf(copied);
    }

    private static final class Session {
        private final List<RecordedQuery> queries = new ArrayList<>();
    }

    public record Capture<T>(T result, List<RecordedQuery> queries) {
        public Capture {
            queries = List.copyOf(queries);
        }

        public RecordedQuery single(QueryKind kind) {
            List<RecordedQuery> matching = queries.stream()
                    .filter(query -> query.kind() == kind)
                    .toList();
            if (matching.size() != 1) {
                throw new AssertionError(
                        "Expected exactly one %s query but captured %d: %s"
                                .formatted(kind, matching.size(), queries.stream()
                                        .map(RecordedQuery::sql)
                                        .toList()));
            }
            return matching.get(0);
        }
    }

    public record RecordedQuery(
            String sql,
            List<ParameterSetOperation> parameters,
            QueryKind kind) {
        public RecordedQuery {
            Objects.requireNonNull(sql, "sql must not be null");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    public enum QueryKind {
        CONTENT,
        COUNT
    }
}
