package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

public final class PostgresExplain {
    private static final Path OUTPUT_DIRECTORY = Path.of("target", "a03-plans");

    private final DataSource dataSource;
    private final PostgresQueryRecorder recorder;
    private final ObjectMapper objectMapper;

    public PostgresExplain(
            DataSource dataSource,
            PostgresQueryRecorder recorder,
            ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    public PostgresPlan explain(
            String scenario,
            PostgresQueryRecorder.RecordedQuery query,
            boolean analyze) {
        return recorder.withoutRecording(() -> execute(scenario, query, analyze));
    }

    private PostgresPlan execute(
            String scenario,
            PostgresQueryRecorder.RecordedQuery query,
            boolean analyze) {
        String options = analyze
                ? "analyze, buffers, format json, timing off"
                : "format json";
        String sql = "explain (" + options + ") " + query.sql();
        try (var connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, query);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("EXPLAIN returned no rows");
                }
                JsonNode document = objectMapper.readTree(resultSet.getString(1));
                write(scenario, query.kind(), analyze, document);
                return new PostgresPlan(document);
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Could not execute or persist PostgreSQL EXPLAIN", exception);
        }
    }

    private static void bind(
            PreparedStatement statement,
            PostgresQueryRecorder.RecordedQuery query) {
        for (ParameterSetOperation operation : query.parameters()) {
            try {
                operation.getMethod().invoke(statement, operation.getArgs());
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException(
                        "Could not replay JDBC binding " + operation.getMethod().getName(),
                        exception);
            }
        }
    }

    private void write(
            String scenario,
            PostgresQueryRecorder.QueryKind kind,
            boolean analyze,
            JsonNode document) throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        String mode = analyze ? "analyze" : "plan";
        Path output = OUTPUT_DIRECTORY.resolve(
                "%s-%s-%s.json".formatted(
                        sanitize(scenario),
                        kind.name().toLowerCase(),
                        mode));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), document);
    }

    private static String sanitize(String scenario) {
        return scenario.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
