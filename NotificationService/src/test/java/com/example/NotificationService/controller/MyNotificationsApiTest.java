package com.example.NotificationService.controller;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 7: GET /notifications/me with real HTTP requests, like the gateway sends them.
 * Whole service; in-memory database, Kafka listeners off, dummy secret.
 * Written first: before the endpoint existed, 7 of 9 failed (404).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "internalsecret=" + MyNotificationsApiTest.SECRET,
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class MyNotificationsApiTest {

    static final String SECRET = "dummy-test-only-secret";

    @Autowired MockMvc mvc;
    @Autowired NotificationLogRepository repository;
    @MockitoBean UserClient userClient;   // not used here, just not started

    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 19, 9, 0);

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    /** A notification row for `recipient`, created `minutesLater` after T0. */
    private NotificationLog row(UUID recipient, int minutesLater) {
        return repository.save(NotificationLog.builder().eventId(UUID.randomUUID()).incidentId(UUID.randomUUID())
                .eventType(EventType.SLA_ASSIGNMENT_BREACHED).status(NotificationStatus.RECORDED)
                .recipientId(recipient).recipientEmail(recipient + "@example.com").recipientName("name")
                .message("message " + minutesLater).createdAt(T0.plusMinutes(minutesLater)).build());
    }

    /** A request exactly like the gateway sends it for a logged-in user. */
    private static MockHttpServletRequestBuilder asUser(UUID userId, String url) {
        return get(url).header("X-Internal-Secret", SECRET).header("X-User-Id", userId.toString())
                .header("X-Roles", "ROLE_ENGINEER");
    }

    // ------------------------------------------------------------------ the answer

    @Test
    void iSeeOnlyMyOwnNotificationsNewestFirstWithTheTotal() throws Exception {
        NotificationLog first = row(ME, 1);
        NotificationLog second = row(ME, 2);
        NotificationLog third = row(ME, 3);
        row(SOMEONE_ELSE, 4);
        row(SOMEONE_ELSE, 5);
        row(null, 6);   // an old row from before step 6: no user id

        mvc.perform(asUser(ME, "/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(third.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.items[0].type").value("SLA_ASSIGNMENT_BREACHED"))
                .andExpect(jsonPath("$.items[0].message").value("message 3"))
                .andExpect(jsonPath("$.items[0].incidentId").value(third.getIncidentId().toString()));
    }

    @Test
    void pagingWorksAndTheTotalCountsEverything() throws Exception {
        for (int i = 1; i <= 5; i++) {
            row(ME, i);
        }

        mvc.perform(asUser(ME, "/notifications/me?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].message").value("message 3"))   // 5,4 on page 0 - 3,2 on page 1
                .andExpect(jsonPath("$.items[1].message").value("message 2"));
    }

    @Test
    void withoutPageAndSizeIGetTheFirst20() throws Exception {
        for (int i = 1; i <= 25; i++) {
            row(ME, i);
        }

        mvc.perform(asUser(ME, "/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(20));
    }

    @Test
    void someoneWithNoNotificationsGetsAnEmptyList() throws Exception {
        row(SOMEONE_ELSE, 1);

        mvc.perform(asUser(ME, "/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void theAnswerHoldsNoEmailAddressOrName() throws Exception {
        row(ME, 1);

        String body = mvc.perform(asUser(ME, "/notifications/me"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("@"), body);
        assertFalse(body.contains("recipientEmail"), body);
        assertFalse(body.contains("recipientName"), body);
    }

    // ------------------------------------------------------------------ bad requests

    @Test
    void aBadPageOrSizeIsAClear400InTheUsualErrorShape() throws Exception {
        for (String query : new String[]{"size=0", "size=101", "page=-1"}) {
            mvc.perform(asUser(ME, "/notifications/me?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))     // the same error shape as every other service
                    .andExpect(jsonPath("$.traceId").exists());
        }
    }

    // ------------------------------------------------------------------ who may ask

    @Test
    void withoutALoginTheRequestIsRefused() throws Exception {
        row(ME, 1);

        mvc.perform(get("/notifications/me")).andExpect(status().isForbidden());
    }

    @Test
    void identityHeadersWithoutTheRightSecretAreNotBelieved() throws Exception {
        row(ME, 1);

        // Someone who reaches the service directly and types the headers himself (Track B1).
        mvc.perform(get("/notifications/me").header("X-Internal-Secret", "wrong")
                        .header("X-User-Id", ME.toString()).header("X-Roles", "ROLE_ENGINEER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherServiceIsNotAUserAndGetsNoList() throws Exception {
        // A service-to-service call is "logged in" under the SERVICE name, not a user id. It has no list.
        mvc.perform(get("/notifications/me").header("X-Internal-Secret", SECRET)
                        .header("X-Internal-Service", "IncidentService"))
                .andExpect(status().isForbidden());
    }
}
