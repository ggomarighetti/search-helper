package io.github.ggomarighetti.rsqljpasearch.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JarBoundaryIT {
    private static final List<ProductModule> PRODUCT_MODULES = List.of(
            new ProductModule("api", "rsql-jpa-search-api"),
            new ProductModule("rsql-spi", "rsql-jpa-search-rsql-spi"),
            new ProductModule("core", "rsql-jpa-search-core"),
            new ProductModule("jpa-validation", "rsql-jpa-search-jpa-validation"),
            new ProductModule("perplexhub", "rsql-jpa-search-perplexhub"),
            new ProductModule("spring-boot-starter", "rsql-jpa-search-spring-boot-starter"));

    @Test
    void productJarsHaveUniqueClassesAndPackages() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        Map<String, String> classOwners = new LinkedHashMap<>();
        Map<String, String> packageOwners = new LinkedHashMap<>();

        for (ProductModule module : PRODUCT_MODULES) {
            Path jar = productJar(root, module);
            assertTrue(Files.isRegularFile(jar), () -> "missing product jar " + jar);
            try (JarFile archive = new JarFile(jar.toFile())) {
                archive.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(java.util.zip.ZipEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .filter(name -> !name.equals("module-info.class"))
                        .forEach(name -> {
                            registerOwner(classOwners, name, module.artifactId(), "class");
                            int separator = name.lastIndexOf('/');
                            if (separator > 0) {
                                registerOwner(
                                        packageOwners,
                                        name.substring(0, separator),
                                        module.artifactId(),
                                        "package");
                            }
                        });
            }
        }

        Path group = root.resolve("target/staging-deploy/io/github/ggomarighetti");
        List<String> unpublishedArtifacts = List.of(
                "jpa-rsql-search",
                "jpa-rsql-search-parent",
                "jpa-rsql-search-api",
                "jpa-rsql-search-rsql-spi",
                "jpa-rsql-search-core",
                "jpa-rsql-search-jpa-validation",
                "jpa-rsql-search-perplexhub",
                "jpa-rsql-search-spring-boot-starter",
                "jpa-rsql-search-integration-tests",
                "rsql-jpa-search",
                "rsql-jpa-search-integration-tests");
        for (String artifact : unpublishedArtifacts) {
            assertFalse(
                    Files.exists(group.resolve(artifact)),
                    () -> "unpublished coordinate must not be generated: " + artifact);
        }
    }

    @Test
    void starterCarriesSpringBootDiscoveryMetadata() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        ProductModule starterModule = PRODUCT_MODULES.get(PRODUCT_MODULES.size() - 1);
        try (JarFile starter = new JarFile(productJar(root, starterModule).toFile())) {
            assertNotNull(starter.getEntry(
                    "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        }
    }

    private static Path productJar(Path root, ProductModule module) {
        return root.resolve(module.directory())
                .resolve("target")
                .resolve(module.artifactId() + "-" + projectVersion() + ".jar");
    }

    private static String projectVersion() {
        String version = System.getProperty("project.version");
        assertNotNull(version, "project.version system property must be configured");
        return version;
    }

    private static void registerOwner(
            Map<String, String> owners,
            String entry,
            String module,
            String kind) {
        String previous = owners.putIfAbsent(entry, module);
        if (previous != null && !previous.equals(module)) {
            fail("%s '%s' exists in both %s and %s".formatted(kind, entry, previous, module));
        }
    }

    private record ProductModule(String directory, String artifactId) {
    }
}
