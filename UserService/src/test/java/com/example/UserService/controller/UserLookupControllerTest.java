package com.example.UserService.controller;

import com.example.UserService.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BCR-05 (2026-11-02): POST /users/lookup turns user ids into names, so screens show "Priya" instead of a long id.
 * Only the web side is tested here (the controller with a fake UserService, no security, no database).
 * UserLookupTest checks the real lookup against a database.
 */
class UserLookupControllerTest {

    private final UserService service = mock(UserService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

    private static String idsJson(int count) {
        return IntStream.range(0, count).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(",", "{\"ids\":[", "]}"));
    }

    @Test
    void aListOfIdsGetsAListBack() throws Exception {
        mvc.perform(post("/users/lookup").contentType(MediaType.APPLICATION_JSON).content(idsJson(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // One screen asks once for all the people on it. 100 is plenty; more would let one call read the whole table.
    @Test
    void oneHundredIdsAreAllowed() throws Exception {
        mvc.perform(post("/users/lookup").contentType(MediaType.APPLICATION_JSON).content(idsJson(100)))
                .andExpect(status().isOk());
    }

    @Test
    void moreThanOneHundredIdsAreRefused() throws Exception {
        mvc.perform(post("/users/lookup").contentType(MediaType.APPLICATION_JSON).content(idsJson(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingListIsRefused() throws Exception {
        mvc.perform(post("/users/lookup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void somethingThatIsNotAnIdIsRefused() throws Exception {
        mvc.perform(post("/users/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"not-an-id\"]}"))
                .andExpect(status().isBadRequest());
    }
}
