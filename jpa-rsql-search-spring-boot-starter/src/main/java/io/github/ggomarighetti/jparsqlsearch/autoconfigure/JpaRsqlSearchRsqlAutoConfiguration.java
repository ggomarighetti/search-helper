package io.github.ggomarighetti.jparsqlsearch.autoconfigure;

import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.perplexhub.PerplexhubRsqlBackendOptions;
import io.github.ggomarighetti.jparsqlsearch.rsql.backend.RsqlBackendAdapter;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngine;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngineBuilder;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngineCustomizer;
import io.github.ggomarighetti.jparsqlsearch.rsql.engine.SearchRsqlEngines;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

@AutoConfiguration
@EnableConfigurationProperties(JpaRsqlSearchProperties.class)
@ConditionalOnProperty(prefix = "jpa.rsql.search.rsql", name = "enabled", havingValue = "true", matchIfMissing = true)
class JpaRsqlSearchRsqlAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RSQLJPASupport rsqlJpaSupport(
            Map<String, EntityManager> entityManagers,
            Map<String, EntityManagerFactory> entityManagerFactories) {
        Map<String, EntityManager> registered = new LinkedHashMap<>(entityManagers);
        entityManagerFactories.forEach((name, entityManagerFactory) -> registered.putIfAbsent(
                name,
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)));
        return new RSQLJPASupport(registered);
    }

    @Bean
    @ConditionalOnMissingBean
    public PerplexhubRsqlBackendOptions perplexhubRsqlBackendOptions(JpaRsqlSearchProperties properties) {
        JpaRsqlSearchProperties.Rsql.Perplexhub perplexhub = properties.getRsql().getPerplexhub();
        return PerplexhubRsqlBackendOptions.builder()
                .strictEquality(perplexhub.isStrictEquality())
                .likeEscapeCharacter(perplexhub.getLikeEscapeCharacter())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RsqlBackendAdapter rsqlBackendAdapter(PerplexhubRsqlBackendOptions options) {
        return new PerplexhubRsqlBackendAdapter(options);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchRsqlEngine searchRsqlEngine(
            ObjectProvider<ConversionService> conversionServices,
            ListableBeanFactory beanFactory,
            RsqlBackendAdapter backend,
            ObjectProvider<SearchRsqlEngineCustomizer> customizers) {
        ConversionService conversionService = conversionServices.getIfUnique();
        if (conversionService == null) {
            conversionService = applicationConversionService(beanFactory);
        }
        SearchRsqlEngineBuilder builder = SearchRsqlEngines.builder(backend)
                .conversionService(conversionService);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    private static ConversionService applicationConversionService(ListableBeanFactory beanFactory) {
        ApplicationConversionService conversionService = new ApplicationConversionService();
        ApplicationConversionService.addBeans(conversionService, beanFactory);
        return conversionService;
    }
}
