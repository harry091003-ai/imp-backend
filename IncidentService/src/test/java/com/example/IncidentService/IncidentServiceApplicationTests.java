package com.example.IncidentService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does IncidentService start? Safe to run any time (option B, 2026-10-19).
 * - In-memory database (H2), Flyway off: your real MySQL is never used.
 * - Kafka at localhost:1 (nothing there), Eureka off, dummy secret.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incident-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1"
})
class IncidentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
