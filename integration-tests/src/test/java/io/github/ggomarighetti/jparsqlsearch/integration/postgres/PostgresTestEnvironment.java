package io.github.ggomarighetti.jparsqlsearch.integration.postgres;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestEnvironment {
    private static final String DATABASE = "jpa_rsql_search";
    private static final String USERNAME = "jpa_rsql_search";
    private static final String PASSWORD = "jpa_rsql_search";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName(DATABASE)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withStartupTimeout(Duration.ofSeconds(120));

    static {
        POSTGRES.start();
    }

    private PostgresTestEnvironment() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    public static String[] springProperties() {
        return new String[] {
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName()
        };
    }
}
