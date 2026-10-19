package com.example.Ai_Service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does Ai-Service start? Safe to run any time (option B, 2026-10-19).
 * - In-memory database (H2, Postgres mode), Flyway off: your real Postgres is never used.
 * - Dummy Gemini key: starting the app makes no AI call (nothing is sent to Google).
 * - Kafka at localhost:1 and listeners not started, Eureka off, dummy secret.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "apikey=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
class AiServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
