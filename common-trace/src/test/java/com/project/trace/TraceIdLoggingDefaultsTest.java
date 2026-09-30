package com.project.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// C1 step 2 (moved to common-trace in step 3): log lines really SHOW the traceId.
// The last test starts real Spring Boot logging and reads what was printed.
@ExtendWith(OutputCaptureExtension.class)
class TraceIdLoggingDefaultsTest {

    @Test
    void addsTheSettingWhenAServiceHasNone() {
        StandardEnvironment environment = new StandardEnvironment();
        new TraceIdLoggingDefaults().postProcessEnvironment(environment, null);

        assertEquals("[%X{traceId:--}] ", environment.getProperty("logging.pattern.correlation"));
    }

    // Lowest priority: a service's own setting must still win.
    @Test
    void aServicesOwnSettingStillWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("service",
                Map.of("logging.pattern.correlation", "[my-own] ")));

        new TraceIdLoggingDefaults().postProcessEnvironment(environment, null);

        assertEquals("[my-own] ", environment.getProperty("logging.pattern.correlation"));
    }

    // The format asks for the MDC key by name. That name must be the SAME word the writers
    // (TraceIdFilter, TraceIdRecordInterceptor) put on the notepad - both read TraceId.MDC_KEY.
    @Test
    void theFormatReadsTheSameKeyTheWritersUse() {
        assertEquals("[%X{" + TraceId.MDC_KEY + ":--}] ", TraceIdLoggingDefaults.PATTERN);
    }

    // The real thing: start Spring Boot, log one line with a traceId on the notepad and one
    // without, then read the console output.
    @Test
    void realLogLinesShowTheTraceId(CapturedOutput output) {
        Logger log = LoggerFactory.getLogger("trace-id-test");

        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(EmptyApp.class)
                .web(WebApplicationType.NONE).run()) {

            MDC.put(TraceId.MDC_KEY, "abc12345");
            try {
                log.info("line inside a request");
            } finally {
                MDC.remove(TraceId.MDC_KEY);
            }
            log.info("line outside a request");
        }

        String inside = lineContaining(output.getOut(), "line inside a request");
        String outside = lineContaining(output.getOut(), "line outside a request");
        assertTrue(inside.contains("[abc12345] "), "inside a request, the line must show the id: " + inside);
        assertTrue(outside.contains("[-] "), "outside a request, the line must show [-]: " + outside);
    }

    private static String lineContaining(String text, String marker) {
        return text.lines().filter(line -> line.contains(marker)).findFirst()
                .orElseThrow(() -> new AssertionError("no log line contains: " + marker));
    }

    @Configuration
    static class EmptyApp {
    }
}
