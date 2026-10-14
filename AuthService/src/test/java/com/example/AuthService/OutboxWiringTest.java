package com.example.AuthService;

import com.example.AuthService.service.LogoutUserService;
import com.project.outbox.OutboxRelay;
import com.project.outbox.OutboxScheduler;
import com.project.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox step 3: the WHOLE AuthService starts with common-outbox on it - next to common-kafka,
 * common-web, security and JPA - and the outbox switches itself on.
 *
 * Runs without MySQL, Kafka or Eureka: an in-memory database stands in, and Kafka is never contacted
 * because the outbox table is empty (the relay has nothing to send).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-wiring;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "secretkey=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1"
})
class OutboxWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void theOutboxSwitchesItselfOnInTheRealService() {
        assertNotNull(context.getBean(OutboxWriter.class));
        assertNotNull(context.getBean(OutboxRelay.class));
        assertTrue(context.getBean(OutboxScheduler.class).isRunning(), "the relay thread is running");
        assertNotNull(context.getBean(LogoutUserService.class), "the service that now needs a TransactionTemplate");
    }
}
