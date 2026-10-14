package com.example.ReportingService;

import com.example.ReportingService.schedular.slaBreached.SLA_BreachedSchedular;
import com.project.outbox.OutboxRelay;
import com.project.outbox.OutboxScheduler;
import com.project.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox step 4: the WHOLE ReportingService starts with common-outbox on it - next to common-kafka,
 * common-web, security, JPA and ShedLock - and the outbox switches itself on.
 *
 * Runs without MySQL, Kafka or Eureka: an in-memory database stands in, the 3 Kafka listeners are told not
 * to start, and Kafka is never contacted because the outbox table is empty (the relay has nothing to send).
 * The 3 SLA timers DO run here (they start at once), through the real ShedLock lock and the real
 * TransactionTemplate - the test checks that all 3 took their lock.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporting-wiring;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class OutboxWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void theOutboxSwitchesItselfOnInTheRealService() {
        assertNotNull(context.getBean(OutboxWriter.class));
        assertNotNull(context.getBean(OutboxRelay.class));
        assertTrue(context.getBean(OutboxScheduler.class).isRunning(), "the relay thread is running");
        assertNotNull(context.getBean(SLA_BreachedSchedular.class), "the scheduler that now needs a TransactionTemplate");
    }

    @Test
    void theThreeSlaTimersRunAndTakeTheirLock() throws InterruptedException {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        List<String> locks = List.of();
        for (int i = 0; i < 100 && locks.size() < 3; i++) {   // wait up to 10 seconds
            Thread.sleep(100);
            locks = jdbc.queryForList("SELECT name FROM shedlock ORDER BY name", String.class);
        }
        assertEquals(List.of("monitorAssignment_SLA_Breaching_Risk", "monitorResolution_SLA_Breaching_Risk",
                "slaScheduler"), locks);
    }
}
