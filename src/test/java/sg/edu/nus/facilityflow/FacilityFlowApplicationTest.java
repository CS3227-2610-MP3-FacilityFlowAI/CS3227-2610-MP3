package sg.edu.nus.facilityflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityFlowApplicationTest {
    @Test
    @DisplayName("NFR-SEC-002 / TECHNICAL-SPEC: database configuration binds without external calls")
    void startsWithoutExternalServices() {
        SpringApplication application = new SpringApplication(FacilityFlowApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        // Explicit test-only values override any developer database environment.
        try (var context = application.run(
                "--spring.profiles.active=test",
                "--spring.flyway.enabled=false",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/isolated_test",
                "--spring.datasource.username=synthetic_test",
                "--spring.datasource.password=synthetic_test",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt")) {
            assertTrue(context.isActive());
            assertEquals("facilityflow-ai", context.getEnvironment().getProperty("spring.application.name"));
            var dataSource = context.getBean(HikariDataSource.class);
            assertEquals("jdbc:postgresql://127.0.0.1:1/isolated_test", dataSource.getJdbcUrl());
            assertEquals("synthetic_test", dataSource.getUsername());
            assertEquals(2, dataSource.getMaximumPoolSize());
            assertEquals("verify-full", dataSource.getDataSourceProperties().getProperty("sslmode"));
            assertEquals("synthetic-test.crt", dataSource.getDataSourceProperties().getProperty("sslrootcert"));
            assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
        }
    }
}
