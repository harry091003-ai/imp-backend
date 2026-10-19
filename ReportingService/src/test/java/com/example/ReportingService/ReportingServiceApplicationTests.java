package com.example.ReportingService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does ReportingService start? Safe to run any time (option B, 2026-10-19).
 * - In-memory database (H2), Flyway off: your real MySQL is never used.
 * - Kafka at localhost:1 and listeners not started: no real messages are read.
 * - Eureka off, dummy secret. The SLA timers run, but only against the in-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporting-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql,classpath:shedlock_for_tests.sql",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
class ReportingServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
