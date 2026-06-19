package io.github.ggomarighetti.jparsqlsearch.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void sonarIntendedArchitectureMapsEveryProductionType() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        JsonNode model = new ObjectMapper()
                .readTree(Files.readString(root.resolve(".sonar/architecture-model.json")));
        Set<String> mappedPatterns = new HashSet<>();
        Set<String> mappedLabels = new HashSet<>();
        model.path("perspectives")
                .path(0)
                .path("groups")
                .forEach(group -> {
                    mappedLabels.add(group.path("label").asText());
                    group.path("patterns")
                            .forEach(pattern -> mappedPatterns.add(pattern.asText()));
                });

        Set<String> expectedPatterns = expectedSonarTypePatterns(root);
        assertEquals(
                expectedPatterns,
                mappedPatterns,
                () -> "Sonar architecture patterns differ from production Java types: " + mappedPatterns);
        assertEquals(
                expectedPatterns,
                mappedLabels,
                () -> "Sonar architecture labels must match the production Java type patterns: " + mappedLabels);
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

    private static Set<String> expectedSonarTypePatterns(Path root) throws IOException {
        Set<String> patterns = new HashSet<>();
        for (String module : PRODUCT_MODULES) {
            Path sourceRoot = root.resolve(module).resolve("src/main/java");
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .map(sourceRoot::relativize)
                        .map(Path::toString)
                        .map(path -> path.replace('\\', '.').replace('/', '.'))
                        .map(path -> module + ":" + path.replaceAll("\\.java$", ""))
                        .forEach(patterns::add);
            }
        }
        return patterns;
    }
}
