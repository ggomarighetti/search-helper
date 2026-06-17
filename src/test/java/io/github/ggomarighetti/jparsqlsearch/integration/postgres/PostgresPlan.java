package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PostgresPlan {
    private final JsonNode document;
    private final List<JsonNode> nodes;

    PostgresPlan(JsonNode document) {
        this.document = Objects.requireNonNull(document, "document must not be null");
        JsonNode root = document.path(0).path("Plan");
        if (root.isMissingNode()) {
            throw new IllegalArgumentException("EXPLAIN JSON does not contain a root Plan");
        }
        List<JsonNode> collected = new ArrayList<>();
        collect(root, collected);
        this.nodes = List.copyOf(collected);
    }

    public JsonNode document() {
        return document;
    }

    public Set<String> nodeTypes() {
        return nodes.stream()
                .map(node -> node.path("Node Type").asText())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean usesIndex(String indexName) {
        return nodes.stream().anyMatch(node -> indexName.equals(node.path("Index Name").asText()));
    }

    public boolean usesAnyIndex() {
        return nodes.stream().anyMatch(node -> {
            String type = node.path("Node Type").asText();
            return type.contains("Index") || type.startsWith("Bitmap");
        });
    }

    public boolean hasSequentialScan(String relation) {
        return nodes.stream().anyMatch(node ->
                "Seq Scan".equals(node.path("Node Type").asText())
                        && relation.equals(node.path("Relation Name").asText()));
    }

    public boolean hasExternalSort() {
        return nodes.stream()
                .map(node -> node.path("Sort Method").asText("").toLowerCase(Locale.ROOT))
                .anyMatch(method -> method.contains("external"));
    }

    public double totalCost() {
        return nodes.get(0).path("Total Cost").asDouble();
    }

    public long sharedHitBlocks() {
        return nodes.get(0).path("Shared Hit Blocks").asLong(0);
    }

    public double executionTimeMillis() {
        JsonNode executionTime = document.path(0).path("Execution Time");
        if (executionTime.isMissingNode()) {
            throw new IllegalStateException("Plan does not contain ANALYZE execution time");
        }
        return executionTime.asDouble();
    }

    public long tempReadBlocks() {
        return sum("Temp Read Blocks");
    }

    public long tempWrittenBlocks() {
        return sum("Temp Written Blocks");
    }

    public double rootEstimateRatio() {
        JsonNode root = nodes.get(0);
        if (!root.has("Actual Rows")) {
            throw new IllegalStateException("Plan does not contain ANALYZE metrics");
        }
        double planned = Math.max(1.0, root.path("Plan Rows").asDouble());
        double actual = Math.max(1.0, root.path("Actual Rows").asDouble());
        return Math.max(planned, actual) / Math.min(planned, actual);
    }

    private long sum(String field) {
        long total = 0;
        for (JsonNode node : nodes) {
            total += node.path(field).asLong(0);
        }
        return total;
    }

    private static void collect(JsonNode node, List<JsonNode> collected) {
        collected.add(node);
        for (JsonNode child : node.path("Plans")) {
            collect(child, collected);
        }
    }
}
