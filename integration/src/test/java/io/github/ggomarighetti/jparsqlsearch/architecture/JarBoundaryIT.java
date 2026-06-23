package io.github.ggomarighetti.jparsqlsearch.architecture;

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
    private static final List<String> PRODUCT_MODULES = List.of(
            "jpa-rsql-search-api",
            "jpa-rsql-search-rsql-spi",
            "jpa-rsql-search-core",
            "jpa-rsql-search-jpa-validation",
            "jpa-rsql-search-perplexhub",
            "jpa-rsql-search-spring-boot-starter");

    @Test
    void productJarsHaveUniqueClassesAndPackages() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        Map<String, String> classOwners = new LinkedHashMap<>();
        Map<String, String> packageOwners = new LinkedHashMap<>();

        for (String module : PRODUCT_MODULES) {
            Path jar = productJar(root, module);
            assertTrue(Files.isRegularFile(jar), () -> "missing product jar " + jar);
            try (JarFile archive = new JarFile(jar.toFile())) {
                archive.stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(java.util.zip.ZipEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .filter(name -> !name.equals("module-info.class"))
                        .forEach(name -> {
                            registerOwner(classOwners, name, module, "class");
                            int separator = name.lastIndexOf('/');
                            if (separator > 0) {
                                registerOwner(packageOwners, name.substring(0, separator), module, "package");
                            }
                        });
            }
        }

        Path retiredCoordinate = root.resolve("target/staging-deploy/io/github/ggomarighetti/jpa-rsql-search");
        assertFalse(Files.exists(retiredCoordinate), "the retired jpa-rsql-search coordinate must not be generated");
    }

    @Test
    void starterCarriesSpringBootDiscoveryMetadata() throws IOException {
        Path root = Path.of(System.getProperty("workspace.root"));
        try (JarFile starter = new JarFile(productJar(root, "jpa-rsql-search-spring-boot-starter").toFile())) {
            assertNotNull(starter.getEntry(
                    "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        }
    }

    private static Path productJar(Path root, String module) {
        return root.resolve(module)
                .resolve("target")
                .resolve(module + "-2.0.0-SNAPSHOT.jar");
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
}
