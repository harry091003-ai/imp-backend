package com.project.trace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

// C1 step 2 (moved here from common-web in step 3):
// makes every log line show the request's traceId - with no setting in any service.
//
// The trace id is saved on a thread's notepad (the MDC) - by TraceIdFilter for an HTTP request,
// by TraceIdRecordInterceptor for a Kafka message. But a log line only shows a notepad value if
// the log FORMAT asks for it, and no service's format did.
//
// Spring Boot's normal log line already has an empty slot for this, just before the class
// name. The setting "logging.pattern.correlation" fills it. This class fills it with the traceId:
//
//   before: ... [nio-8083-exec-1] c.e.A.service.LogoutUserService : User ... logged out
//   after:  ... [nio-8083-exec-1] [a1b2c3d4] c.e.A.service.LogoutUserService : User ... logged out
//
// Nothing running for a request or a message (startup)? Then the slot shows [-].
//
// Why it lives in common-trace (step 3): it used to be in common-web, but NotificationService
// (Kafka only, no common-web) needs the same slot. common-web and common-kafka both depend on
// common-trace, so every service gets it - from whichever of the two it already uses.
//
// Added LAST = lowest priority. A service can still override it in its own properties.
// Registered in META-INF/spring.factories. Spring Boot runs it BEFORE it sets up logging.
public class TraceIdLoggingDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY = "logging.pattern.correlation";

    // %X{traceId:--} = "print the MDC value traceId, or '-' if there is none".
    // Built from TraceId.MDC_KEY, so the name the writers use and the name printed can't differ.
    static final String PATTERN = "[%X{" + TraceId.MDC_KEY + ":--}] ";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(
                new MapPropertySource("common-trace-logging", Map.of(PROPERTY, PATTERN)));
    }
}
