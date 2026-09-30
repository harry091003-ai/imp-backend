package com.project.web.config;

import com.project.web.exception.ConflictException;
import com.project.web.filter.TraceIdFilter;
import com.project.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// C1 step 2 (the format tests moved to common-trace in step 3; this end-to-end one stays here
// because it needs TraceIdFilter and GlobalExceptionHandler, which live in common-web).
//
// The whole chain, with real Spring Boot logging, and the log-format default coming from the
// common-trace jar on the classpath - exactly as it does inside a real service:
//   request with X-Trace-Id -> TraceIdFilter saves it -> controller throws ->
//   GlobalExceptionHandler logs -> the printed line shows the id EXACTLY ONCE.
// (Once, because step 2 removed the handler's hand-written "[traceId]".)
@ExtendWith(OutputCaptureExtension.class)
class TraceIdLoggingDefaultsTest {

    @Test
    void wholeChainPrintsTheIdOnceOnTheHandlersLine(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(EmptyApp.class)
                .web(WebApplicationType.NONE).run()) {

            MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                    .addFilters(new TraceIdFilter())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

            mvc.perform(get("/fail").header("X-Trace-Id", "e2e12345"))
                    .andExpect(status().isConflict());
        }

        String line = output.getOut().lines().filter(l -> l.contains("GET /fail -> 409")).findFirst()
                .orElseThrow(() -> new AssertionError("the handler did not log the 409"));
        assertTrue(line.contains("[e2e12345] "), "the handler's line must show the id: " + line);
        assertEquals(1, line.split("e2e12345", -1).length - 1, "the id must appear exactly once: " + line);
    }

    @RestController
    static class FailingController {
        @GetMapping("/fail")
        String fail() {
            throw new ConflictException("TEST_CONFLICT", "made to fail");
        }
    }

    @Configuration
    static class EmptyApp {
    }
}
