package io.github.ggomarighetti.jparsqlsearch.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
