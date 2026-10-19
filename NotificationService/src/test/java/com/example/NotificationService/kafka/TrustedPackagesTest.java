package com.example.NotificationService.kafka;

import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue 65: which event classes this service may read from Kafka.
 * - Reads the REAL trusted-packages setting, then writes and reads each event like Kafka does.
 * - The check is an EXACT package match, so the SLA risk event's sub-folder must be listed.
 * - Plain Java: no Kafka, no database. Written first: the risk event failed before the fix.
 */
class TrustedPackagesTest {

    private static String trustedPackages;

    @BeforeAll
    static void readTheRealSetting() throws Exception {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        trustedPackages = props.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages");
        assertNotNull(trustedPackages, "the setting must exist in application.properties");
    }

    /** Write the event like a producer does, then read it back like our listeners do. */
    private static Object writeAndRead(Object event) {
        RecordHeaders headers = new RecordHeaders();
        byte[] bytes;
        try (JsonSerializer<Object> writer = new JsonSerializer<>()) {
            bytes = writer.serialize("any-topic", headers, event);   // puts the class name in a header
        }
        try (JsonDeserializer<Object> reader = new JsonDeserializer<>()) {
            reader.configure(Map.of(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages), false);
            return reader.deserialize("any-topic", headers, bytes);
        }
    }

    // ------------------------------------------------------------------ the fix

    @Test
    void theSlaRiskEventFromTheSubPackageIsAccepted() {
        UUID incidentId = UUID.randomUUID();
        SlaBreachingRiskEvent event = SlaBreachingRiskEvent.builder().incidentId(incidentId)
                .previousRiskLevel("HIGH").currentRiskLevel("CRITICAL").riskScore(0.95)
                .type("ASSIGNMENT_SLA_BREACHING_RISK").build();

        Object read = writeAndRead(event);

        assertInstanceOf(SlaBreachingRiskEvent.class, read);
        assertEquals(incidentId, ((SlaBreachingRiskEvent) read).getIncidentId());
        assertEquals("CRITICAL", ((SlaBreachingRiskEvent) read).getCurrentRiskLevel());
    }

    // ------------------------------------------------------------------ what must keep working

    @Test
    void theSlaBreachedEventIsAccepted() {
        UUID incidentId = UUID.randomUUID();
        Object read = writeAndRead(SLA_BreachedEvent.builder().incidentId(incidentId)
                .SLA_BreachedType("RESOLUTION_SLA_BREACHED").build());

        assertInstanceOf(SLA_BreachedEvent.class, read);
        assertEquals("RESOLUTION_SLA_BREACHED", ((SLA_BreachedEvent) read).getSLA_BreachedType());
    }

    @Test
    void theThreeIncidentEventsWeReadTodayAreStillAccepted() {
        UUID incidentId = UUID.randomUUID();
        assertInstanceOf(IncidentCreatedEvent.class,
                writeAndRead(IncidentCreatedEvent.builder().incidentId(incidentId).title("t").build()));
        assertInstanceOf(IncidentAssignedEvent.class,
                writeAndRead(IncidentAssignedEvent.builder().incidentId(incidentId).title("t").build()));
        assertInstanceOf(IncidentStatusUpdatedEvent.class,
                writeAndRead(IncidentStatusUpdatedEvent.builder().incidentId(incidentId).title("t").build()));
    }

    // ------------------------------------------------------------------ the safety rule must stay

    @Test
    void aClassFromAnyOtherPackageIsStillRejected() {
        // The whole point of the setting (Track B2): a message cannot make us build any class it likes.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> writeAndRead(new NotOurEvent("x")));
        assertTrue(error.getMessage().contains("not in the trusted packages"), error.getMessage());
    }

    @Test
    void theSettingIsAnExactListNotEverything() {
        assertFalse(trustedPackages.contains("*"), "never trust every package: " + trustedPackages);
    }

    /** A class outside com.project.events, standing in for something a stranger might send. */
    public static class NotOurEvent {
        private String value;
        public NotOurEvent() { }
        public NotOurEvent(String value) { this.value = value; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
