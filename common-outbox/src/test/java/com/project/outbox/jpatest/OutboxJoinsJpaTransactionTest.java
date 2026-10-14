package com.project.outbox.jpatest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE key promise of the outbox: the event row and the business data are saved together,
 * or not at all. Proved here with real JPA (Hibernate) + JdbcTemplate + jakarta @Transactional,
 * exactly the mix our services have.
 */
@SpringBootTest(classes = JpaTestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox-jpa;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql",
        "outbox.enabled=false"          // only the writer is under test here, not the relay
})
class OutboxJoinsJpaTransactionTest {

    @Autowired
    JpaTestApp.OrderService orderService;
    @Autowired
    JpaTestApp.TestOrderRepository orders;
    @Autowired
    JdbcTemplate jdbc;

    private int outboxRowsFor(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE msg_key = ?", Integer.class, id.toString());
    }

    @Test
    void whenTheTransactionCommitsBothAreSaved() {
        UUID id = UUID.randomUUID();

        orderService.placeOrder(id, false);

        assertTrue(orders.existsById(id), "the business row is saved");
        assertEquals(1, outboxRowsFor(id), "the event row is saved");
    }

    @Test
    void whenTheTransactionRollsBackNeitherIsSaved() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> orderService.placeOrder(id, true));

        assertFalse(orders.existsById(id), "no business row");
        assertEquals(0, outboxRowsFor(id), "no event row either - so no ghost event can ever be sent");
    }
}
