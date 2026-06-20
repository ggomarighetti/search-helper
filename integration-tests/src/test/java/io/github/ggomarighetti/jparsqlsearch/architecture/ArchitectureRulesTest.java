package io.github.ggomarighetti.jparsqlsearch.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {
    private static final List<String> PRODUCT_MODULES = List.of(
            "jpa-rsql-search-api",
            "jpa-rsql-search-rsql-spi",
            "jpa-rsql-search-core",
            "jpa-rsql-search-jpa-validation",
            "jpa-rsql-search-perplexhub",
            "jpa-rsql-search-spring-boot-starter");

    private final JavaClasses classes =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("io.github.ggomarighetti.jparsqlsearch");

    @Test
    void productionModulesAreInternallyAcyclic() {
        Path root = Path.of(System.getProperty("workspace.root"));
        for (String module : PRODUCT_MODULES) {
            JavaClasses moduleClasses = new ClassFileImporter()
                    .importPath(root.resolve(module).resolve("target/classes"));
            slices().matching("io.github.ggomarighetti.jparsqlsearch.(*)..")
                    .should().beFreeOfCycles()
                    .check(moduleClasses);
        }
    }

    @Test
    void sonarIntendedArchitectureModelsTheV2MavenReactor() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        JsonNode model = new ObjectMapper()
                .readTree(Files.readString(root.resolve(".sonar/architecture-model.json")));
        JsonNode perspective = model.path("perspectives").path(0);
        assertEquals(
                "V2 Maven modules",
                perspective.path("label").asText(),
                "Sonar should display the publishable v2 Maven reactor as the intended architecture.");
        Map<String, String> expectedGroups = new LinkedHashMap<>();
        expectedGroups.put("API", "jpa-rsql-search-api/src/main/java/**");
        expectedGroups.put("RSQL SPI", "jpa-rsql-search-rsql-spi/src/main/java/**");
        expectedGroups.put("Core", "jpa-rsql-search-core/src/main/java/**");
        expectedGroups.put("JPA validation", "jpa-rsql-search-jpa-validation/src/main/java/**");
        expectedGroups.put("Perplexhub", "jpa-rsql-search-perplexhub/src/main/java/**");
        expectedGroups.put("Spring Boot starter", "jpa-rsql-search-spring-boot-starter/src/main/java/**");
        Map<String, String> actualGroups = new LinkedHashMap<>();
        perspective
                .path("groups")
                .forEach(group -> {
                    String label = group.path("label").asText();
                    assertEquals(
                            1,
                            group.path("patterns").size(),
                            () -> "Every Sonar architecture module group must declare exactly one source root: "
                                    + label);
                    actualGroups.put(label, group.path("patterns").path(0).asText());
                });
        assertEquals(expectedGroups, actualGroups);
        assertEquals(5, perspective.path("constraints").size(), "The intended architecture must encode the v2 DAG.");
        perspective.path("constraints").forEach(constraint -> assertTrue(
                Set.of("exclusive-allow").contains(constraint.path("relation").asText()),
                () -> "Unexpected Sonar architecture relation: " + constraint));
        assertEquals(0, model.path("constraints").size(), "The SonarCloud intended architecture API expects perspective-scoped constraints.");
    }

    @Test
    void capabilitiesDoNotDependOnDefinitionComposition() {
        noClasses().that().resideInAnyPackage(
                        "..filter..",
                        "..sort..")
                .should().dependOnClassesThat().resideInAPackage("..definition..")
                .check(classes);
        noClasses().that().resideInAPackage("io.github.ggomarighetti.jparsqlsearch.validation..")
                .should().dependOnClassesThat().resideInAPackage("..definition..")
                .check(classes);
    }

    @Test
    void backendSpiDoesNotDependOnEngine() {
        noClasses().that().resideInAPackage("..rsql.backend..")
                .and().resideOutsideOfPackage("..rsql.backend.perplexhub..")
                .should().dependOnClassesThat().resideInAPackage("..rsql.engine..")
                .check(classes);
    }

    @Test
    void coreDoesNotDependOnPerplexhub() {
        noClasses().that().resideInAnyPackage("..compile..", "..rsql.engine..")
                .should().dependOnClassesThat().resideInAPackage("..rsql.backend.perplexhub..")
                .check(classes);
    }

    @Test
    void operatorMetadataDoesNotDependOnJpaBindings() {
        noClasses().that().resideInAPackage("..rsql.metadata..")
                .should().dependOnClassesThat().resideInAPackage("..rsql.jpa..")
                .check(classes);
    }
}
