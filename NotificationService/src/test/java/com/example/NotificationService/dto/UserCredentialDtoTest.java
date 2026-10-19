package com.example.NotificationService.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 3: both copies of UserCredentialDTO must use the same field name, supervisorId.
 * Also: an answer without the field (an older UserService) still works.
 */
class UserCredentialDtoTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void theSupervisorIdFromUserServiceIsRead() throws Exception {
        UUID user = UUID.randomUUID();
        UUID supervisor = UUID.randomUUID();
        String reply = "{\"userid\":\"" + user + "\",\"username\":\"eva\",\"email\":\"eva@example.com\","
                + "\"supervisorId\":\"" + supervisor + "\"}";

        UserCredentialDTO dto = json.readValue(reply, UserCredentialDTO.class);

        assertEquals(supervisor, dto.getSupervisorId());
        assertEquals(user, dto.getUserid());
    }

    @Test
    void anAnswerFromAnOlderUserServiceWithoutTheFieldStillWorks() throws Exception {
        // If UserService is not restarted yet, its answer has no supervisorId: that must not break anything.
        String reply = "{\"userid\":\"" + UUID.randomUUID() + "\",\"username\":\"eva\",\"email\":\"eva@example.com\"}";

        UserCredentialDTO dto = json.readValue(reply, UserCredentialDTO.class);

        assertNull(dto.getSupervisorId());
        assertEquals("eva@example.com", dto.getEmail());
    }
}
