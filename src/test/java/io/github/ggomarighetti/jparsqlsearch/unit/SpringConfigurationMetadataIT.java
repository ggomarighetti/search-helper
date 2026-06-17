package io.github.ggomarighetti.jparsqlsearch.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class SpringConfigurationMetadataIT {
    private static final String METADATA_PATH = "META-INF/spring-configuration-metadata.json";

    @Test
    void publishedJarContainsDescribedSpringConfigurationMetadata() throws Exception {
        Path jar = projectJar();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(METADATA_PATH);
            assertThat(entry).as("Spring Boot configuration metadata").isNotNull();

            try (InputStream input = zip.getInputStream(entry)) {
                JsonNode properties = new ObjectMapper().readTree(input).path("properties");
                assertThat(properties.isArray()).isTrue();
                assertThat(properties.size()).isGreaterThanOrEqualTo(62);
                assertThat(properties)
                        .allSatisfy(property -> assertThat(property.path("description").asText())
                                .as(property.path("name").asText())
                                .isNotBlank());

                assertProperty(properties, "jpa.rsql.search.rsql.enabled", "java.lang.Boolean", "true");
                assertProperty(
                        properties,
                        "jpa.rsql.search.filter.max-comparisons",
                        "java.lang.Integer",
                        null);
                assertProperty(
                        properties,
                        "jpa.rsql.search.rsql.perplexhub.strict-equality",
                        "java.lang.Boolean",
                        "true");
            }
        }
    }

    private static Path projectJar() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            return files.filter(path -> path.getFileName().toString().matches("jpa-rsql-search-[^/]+\\.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("project JAR was not built before integration tests"));
        }
    }

    private static void assertProperty(
            JsonNode properties,
            String name,
            String type,
            String defaultValue) {
        JsonNode property = findProperty(properties, name);
        assertThat(property.path("type").asText()).isEqualTo(type);
        if (defaultValue != null) {
            assertThat(property.path("defaultValue").asText()).isEqualTo(defaultValue);
        }
    }

    private static JsonNode findProperty(JsonNode properties, String name) {
        for (JsonNode property : properties) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        throw new AssertionError("missing configuration property: " + name);
    }
}
