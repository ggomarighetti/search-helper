package io.github.ggomarighetti.jparsqlsearch.integration;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

final class JpaRsqlSearchTestAutoConfigurationImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {
            "io.github.ggomarighetti.jparsqlsearch.autoconfigure.JpaRsqlSearchRsqlAutoConfiguration",
            "io.github.ggomarighetti.jparsqlsearch.autoconfigure.JpaRsqlSearchAutoConfiguration"
        };
    }
}
