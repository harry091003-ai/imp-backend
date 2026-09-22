package com.project.web.handler;

import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.UnauthorizedException;
import com.project.web.exception.ValidationException;
import com.project.web.filter.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
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
    // B4 step 1: bad input must get a 4xx, never a 500.
    // Before this step these all fell into the catch-all and came back as 500,
    // logged as ERROR with a stack trace, as if the bug were ours.
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("@Min on a query param fails -> 400 with the param name")
    void queryParamValidationMapsTo400() throws Exception {
        mockMvc.perform(get("/dummy/trend").param("days", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("days"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("days must be at most 90"));
    }

    @Test
    @DisplayName("@Size on a request-body list fails -> 400")
    void bodyListTooLongMapsTo400() throws Exception {
        mockMvc.perform(post("/dummy/batch")
                        .contentType("application/json")
                        .content("[\"a\",\"b\",\"c\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("wrong HTTP method -> 405")
    void wrongMethodMapsTo405() throws Exception {
        mockMvc.perform(post("/dummy/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("wrong Content-Type -> 415")
    void wrongContentTypeMapsTo415() throws Exception {
        mockMvc.perform(post("/dummy/validate")
                        .contentType("text/plain")
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("unknown URL (what a real service throws) -> 404 in our JSON shape")
    void unknownUrlMapsTo404() throws Exception {
        mockMvc.perform(get("/dummy/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("a huge rejected value is cut to 200 chars, never echoed in full")
    void hugeRejectedValueIsTruncated() throws Exception {
        String huge = "x".repeat(5000);
        mockMvc.perform(post("/dummy/validate")
                        .contentType("application/json")
                        .content("{\"title\":\"ok\",\"note\":\"" + huge + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("note"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value("x".repeat(200) + "..."));
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

        @GetMapping("/trend")
        void trend(@RequestParam("days") @Min(value = 1, message = "days must be at least 1")
                   @Max(value = 90, message = "days must be at most 90") int days) {
            // never reached in the test - validation fails first
        }

        @PostMapping("/batch")
        void batch(@RequestBody @Size(max = 2, message = "at most 2 ids per call") List<String> ids) {
            // never reached in the test - validation fails first
        }

        // A real Spring Boot service answers an unknown URL by throwing this
        // (from its static-resource handler). This dummy MockMvc has no such
        // handler, so we throw it ourselves to test the same path.
        @GetMapping("/no-such-page")
        void noSuchPage() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "dummy/no-such-page");
        }
    }

    static class DummyRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @Size(max = 10, message = "Note is too long")
        private String note;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
