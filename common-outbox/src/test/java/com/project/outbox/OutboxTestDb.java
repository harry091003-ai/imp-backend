package com.project.outbox;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A fresh in-memory database (H2, in MySQL mode) for one test, with the outbox table created
 * from the SAME sql file the services will copy into Flyway.
 */
final class OutboxTestDb {

    final DriverManagerDataSource dataSource;
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager transactionManager;
    final TransactionTemplate tx;

    private OutboxTestDb(DriverManagerDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        this.tx = new TransactionTemplate(transactionManager);
    }

    static OutboxTestDb create() {
        String url = "jdbc:h2:mem:outbox-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("outbox/outbox_event_mysql.sql")).execute(dataSource);
        return new OutboxTestDb(dataSource);
    }

    Map<String, Object> row(String id) {
        return jdbc.queryForMap("SELECT * FROM outbox_event WHERE id = ?", id);
    }

    String status(String id) {
        return jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, id);
    }

    int attempts(String id) {
        return jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, id);
    }

    int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    /** Insert a row by hand, to set up odd situations (old rows, broken rows, half-sent rows). */
    String insertRaw(String topic, String eventType, String payload, String status,
                     LocalDateTime createdAt, LocalDateTime claimedAt, LocalDateTime sentAt) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO outbox_event (id, topic, msg_key, event_type, payload, status, attempts, "
                        + "created_at, claimed_at, sent_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                id, topic, "k", eventType, payload, status, ts(createdAt), ts(claimedAt), ts(sentAt));
        return id;
    }

    private static Timestamp ts(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }
}
