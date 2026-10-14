package com.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.support.JacksonUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The writer: saves one row, only inside a transaction, with everything the relay needs. */
class OutboxWriterTest {

    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
    private OutboxTestDb db;
    private OutboxWriter writer;

    @BeforeEach
    void setUp() {
        db = OutboxTestDb.create();
        writer = new OutboxWriter(db.jdbc, mapper);
    }

    @AfterEach
    void clearNotepad() {
        MDC.clear();
    }

    @Test
    void savesOneNewRowWithEverythingTheRelayNeeds() {
        TestOrderEvent event = TestOrderEvent.named("first order");

        String id = db.tx.execute(status -> writer.save("orders", "key-1", event));

        Map<String, Object> row = db.row(id);
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals("orders", row.get("TOPIC"));
        assertEquals("key-1", row.get("MSG_KEY"));
        assertEquals(TestOrderEvent.class.getName(), row.get("EVENT_TYPE"));
        assertEquals(OutboxStatus.NEW, row.get("STATUS"));
        assertEquals(0, row.get("ATTEMPTS"));
        assertNotNull(row.get("CREATED_AT"));
        assertNull(row.get("CLAIMED_AT"));
        assertNull(row.get("SENT_AT"));
        assertTrue(row.get("PAYLOAD").toString().contains("first order"));
    }

    @Test
    void theSavedJsonTurnsBackIntoTheSameEvent() throws Exception {
        TestOrderEvent event = TestOrderEvent.named("round trip");

        String id = db.tx.execute(status -> writer.save("orders", "k", event));
        TestOrderEvent back = mapper.readValue(db.row(id).get("PAYLOAD").toString(), TestOrderEvent.class);

        assertEquals(event.getOrderId(), back.getOrderId());
        assertEquals(event.getName(), back.getName());
        assertEquals(event.getCreatedAt(), back.getCreatedAt());   // dates survive, to the millisecond
    }

    @Test
    void refusesToWriteOutsideATransaction() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> writer.save("orders", "k", TestOrderEvent.named("x")));

        assertTrue(error.getMessage().contains("transaction"));
        assertEquals(0, db.count());
    }

    @Test
    void keepsTheTraceIdOfTheRequest() {
        MDC.put("traceId", "abc-123");

        String id = db.tx.execute(status -> writer.save("orders", "k", TestOrderEvent.named("x")));

        assertEquals("abc-123", db.row(id).get("TRACE_ID"));
    }

    @Test
    void dropsAnUnsafeTraceId() {
        MDC.put("traceId", "bad id <script>");

        String id = db.tx.execute(status -> writer.save("orders", "k", TestOrderEvent.named("x")));

        assertNull(db.row(id).get("TRACE_ID"));
    }

    @Test
    void aRolledBackTransactionLeavesNoRow() {
        assertThrows(IllegalStateException.class, () -> db.tx.executeWithoutResult(status -> {
            writer.save("orders", "k", TestOrderEvent.named("x"));
            throw new IllegalStateException("the business code failed after saving");
        }));

        assertEquals(0, db.count());
    }
}
