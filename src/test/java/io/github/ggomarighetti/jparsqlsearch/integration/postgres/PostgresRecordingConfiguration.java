package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresRecordingConfiguration {
    @Bean
    PostgresQueryRecorder postgresQueryRecorder() {
        return new PostgresQueryRecorder();
    }

    @Bean
    static BeanPostProcessor recordingDataSourcePostProcessor(
            ObjectProvider<PostgresQueryRecorder> recorderProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource dataSource) {
                    return ProxyDataSourceBuilder.create(dataSource)
                            .name("jpa-rsql-search-postgres")
                            .listener(recorderProvider.getObject())
                            .build();
                }
                return bean;
            }
        };
    }
}
