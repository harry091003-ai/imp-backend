package com.example.NotificationService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does NotificationService start? Safe to run any time (option B, 2026-10-19).
 * - In-memory database (H2), Flyway off: your real MySQL is never used.
 * - Kafka at localhost:1 and listeners not started: no real messages are read.
 * - Eureka off, dummy secret.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
