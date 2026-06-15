package io.github.ggomarighetti.searchhelper.compile;

import static org.junit.jupiter.api.Assertions.fail;

import io.github.ggomarighetti.searchhelper.definition.SearchDefinition;
import io.github.ggomarighetti.searchhelper.integration.bench.domain.Product;
import io.github.ggomarighetti.searchhelper.property.SearchPropertyFixtures;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RsqlRegressionCorpusTest {
    @Test
    void weirdCorpusNeverEscapesUnexpectedThrowable() throws Exception {
        RsqlSearchGuard guard = new RsqlSearchGuard();
        SearchDefinition<Product> definition = SearchPropertyFixtures.rsqlDefinition();

        for (String input : corpus()) {
            try {
                guard.specification(input, definition);
            } catch (Throwable throwable) {
                if (!SearchPropertyFixtures.isExpectedRsqlThrowable(throwable)) {
                    fail("Unexpected throwable for corpus input [" + escaped(input) + "]", throwable);
                }
            }
        }
    }

    private static List<String> corpus() throws IOException, URISyntaxException {
        Path path = Path.of(RsqlRegressionCorpusTest.class
                .getResource("/corpus/rsql-weird-corpus.txt")
                .toURI());
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.startsWith("#"))
                .map(RsqlRegressionCorpusTest::decode)
                .toList();
    }

    private static String decode(String line) {
        return switch (line) {
            case "<EMPTY>" -> "";
            case "<SPACE>" -> " ";
            case "<TAB>" -> "\t";
            case "<SNOWMAN>" -> "\u2603";
            case "<BACKSLASH>" -> "\\";
            default -> line;
        };
    }

    private static String escaped(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
