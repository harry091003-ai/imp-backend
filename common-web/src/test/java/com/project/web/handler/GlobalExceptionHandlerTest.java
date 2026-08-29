package com.project.web.handler;

import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.UnauthorizedException;
import com.project.web.exception.ValidationException;
import com.project.web.filter.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks down the platform's error contract.
 *
 * <p>This is deliberately a standalone MockMvc test: it wires nothing but the dummy controller
 * below, the {@link TraceIdFilter} and the real {@link GlobalExceptionHandler}. No Spring Boot
 * context, no database, no Kafka, no Eureka - so it runs anywhere in about a second.</p>
 *
 * <p>If someone later changes a status code or renames a JSON field, this test fails and tells
 * them they just broke every client of all eight services.</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    // -----------------------------------------------------------------------------
    // the five business exceptions map to the five statuses
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("NotFoundException -> 404 with its error code and message")
    void notFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/dummy/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("INCIDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Incident not found: 42"))
                .andExpect(jsonPath("$.path").value("/dummy/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                // fieldErrors must be ABSENT (not null) for non-validation errors
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("ValidationException -> 400")
    void validationMapsTo400() throws Exception {
        mockMvc.perform(get("/dummy/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TARGET_USER_NOT_ENGINEER"));
    }

    @Test
    @DisplayName("ConflictException -> 409")
    void conflictMapsTo409() throws Exception {
        mockMvc.perform(get("/dummy/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("ForbiddenException -> 403")
    void forbiddenMapsTo403() throws Exception {
        mockMvc.perform(get("/dummy/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INCIDENT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("UnauthorizedException -> 401")
    void unauthorizedMapsTo401() throws Exception {
        mockMvc.perform(get("/dummy/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("default error code is used when the throw site does not supply one")
    void defaultErrorCode() throws Exception {
        mockMvc.perform(get("/dummy/not-found-default-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // -----------------------------------------------------------------------------
    // framework failures
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("@Valid failure -> 400 listing each rejected field")
    void bodyValidationMapsTo400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/dummy/validate")
                        .contentType("application/json")
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Title is required"));
    }

    @Test
    @DisplayName("a non-UUID path variable -> 400, not the old confusing 500")
    void badUuidMapsTo400() throws Exception {
        mockMvc.perform(get("/dummy/uuid/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("malformed JSON body -> 400 without echoing Jackson internals")
    void malformedJsonMapsTo400() throws Exception {
        mockMvc.perform(post("/dummy/validate")
                        .contentType("application/json")
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    @DisplayName("DB constraint violation -> 409 and the SQL detail is NOT sent to the client")
    void dataIntegrityMapsTo409AndHidesSql() throws Exception {
        mockMvc.perform(get("/dummy/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("This operation conflicts with existing data."));
    }

    // -----------------------------------------------------------------------------
    // the security boundary: unexpected failures must not leak anything
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("an unexpected exception -> 500 with a generic message, never the real one")
    void unexpectedExceptionIsNotLeaked() throws Exception {
        mockMvc.perform(get("/dummy/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please quote the traceId when reporting this."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // -----------------------------------------------------------------------------
    // traceId plumbing
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("an inbound X-Trace-Id is reused, so one id follows the whole call chain")
    void inboundTraceIdIsReused() throws Exception {
        mockMvc.perform(get("/dummy/not-found").header("X-Trace-Id", "abcd1234"))
                .andExpect(jsonPath("$.traceId").value("abcd1234"))
                .andExpect(header().string("X-Trace-Id", "abcd1234"));
    }

    // -----------------------------------------------------------------------------

    @RestController
    @RequestMapping("/dummy")
    static class DummyController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("INCIDENT_NOT_FOUND", "Incident not found: 42");
        }

        @GetMapping("/not-found-default-code")
        void notFoundDefault() {
            throw new NotFoundException("nothing here");
        }

        @GetMapping("/invalid")
        void invalid() {
            throw new ValidationException("TARGET_USER_NOT_ENGINEER", "not an engineer");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("USERNAME_TAKEN", "username already exists");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("INCIDENT_ACCESS_DENIED", "no access");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("BAD_CREDENTIALS", "invalid credentials");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException(
                    "Duplicate entry 'bob' for key 'users.UK_username' [insert into users ...]");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException(
                    "jdbc:mysql://localhost:3306/inicident-management-userservicedb password=hunter2");
        }

        @GetMapping("/uuid/{id}")
        void uuid(@org.springframework.web.bind.annotation.PathVariable UUID id) {
            // never reached in the test - conversion fails first
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody DummyRequest request) {
            // never reached in the test - validation fails first
        }
    }

    static class DummyRequest {
        @NotBlank(message = "Title is required")
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
