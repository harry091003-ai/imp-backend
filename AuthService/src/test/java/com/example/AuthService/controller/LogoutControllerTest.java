package com.example.AuthService.controller;

import com.example.AuthService.service.LogoutUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 5 (2026-10-28): /logout/user accepted EVERY HTTP method, GET included.
 * A GET must never change anything: a browser, a link preview or a prefetch can send one without
 * the user meaning to - and here it would log them out. Now only POST logs out.
 * Only the URL mapping is tested here (no security, no database): the controller with a fake service.
 */
class LogoutControllerTest {

    private final LogoutUserService service = mock(LogoutUserService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new LogoutController(service)).build();

    @Test
    void postLogsOut() throws Exception {
        mvc.perform(post("/logout/user")).andExpect(status().isOk());
        verify(service).LogoutUser();
    }

    @Test
    void getIsRefusedAndLogsNobodyOut() throws Exception {
        mvc.perform(get("/logout/user")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void deleteAndPutAreRefusedToo() throws Exception {
        mvc.perform(delete("/logout/user")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/logout/user")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
