package com.example.UserService.controller;

import com.example.UserService.dto.UserCredentialDTO;
import com.example.UserService.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 2 (2026-10-28): GET /internal/UserSpecificDetails/{userId} could never answer.
 * The URL said {userId} but the Java parameter was named userid, so Spring found no value for it.
 * Only the URL mapping is tested here (no security, no database): the controller with a fake UserService.
 */
class InternalUserControllerTest {

    private final UserService service = mock(UserService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalUserController(service)).build();

    @Test
    void userSpecificDetailsAnswersForTheIdInTheUrl() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.GetUserCredentials(id)).thenReturn(
                UserCredentialDTO.builder().userid(id).username("pat").email("pat@example.com").build());

        mvc.perform(get("/internal/UserSpecificDetails/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userid").value(id.toString()))
                .andExpect(jsonPath("$.email").value("pat@example.com"));
    }

    @Test
    void aValueThatIsNotAnIdIsRefusedWith400() throws Exception {
        mvc.perform(get("/internal/UserSpecificDetails/{userId}", "not-an-id"))
                .andExpect(status().isBadRequest());
    }
}
