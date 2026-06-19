package io.github.ggomarighetti.jparsqlsearch.autoconfigure;

import io.github.ggomarighetti.jparsqlsearch.definition.SearchDefinitionFactory;
import io.github.ggomarighetti.jparsqlsearch.compile.SearchCompiler;
import io.github.ggomarighetti.jparsqlsearch.jpa.JpaSearchDefinitionValidator;
import io.github.ggomarighetti.jparsqlsearch.rsql.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.validation.SearchDefinitionValidator;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
}, after = JpaRsqlSearchRsqlAutoConfiguration.class)
@EnableConfigurationProperties(JpaRsqlSearchProperties.class)
class JpaRsqlSearchAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SearchDefinitionFactory searchDefinitionFactory(JpaRsqlSearchProperties properties) {
        return new SearchDefinitionFactory(properties.toPolicy());
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnMissingBean
    public JpaSearchDefinitionValidator jpaSearchDefinitionValidator(EntityManagerFactory entityManagerFactory) {
        return new JpaSearchDefinitionValidator(entityManagerFactory);
    }

    @Bean
    @ConditionalOnBean(SearchRsqlEngine.class)
    @ConditionalOnMissingBean
    public SearchCompiler searchCompiler(
            SearchRsqlEngine engine,
            ObjectProvider<SearchDefinitionValidator> definitionValidators,
            JpaRsqlSearchProperties properties) {
        return new SearchCompiler(
                engine,
                properties.toPolicy(),
                definitionValidators.orderedStream().toList());
    }
}
