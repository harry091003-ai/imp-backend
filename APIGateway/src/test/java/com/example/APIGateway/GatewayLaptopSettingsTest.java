package com.example.APIGateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BCR-04 (2026-11-02): with NO profile (the laptop), the gateway works exactly as before the production settings:
 * /actuator answers on the main port, and the gateway's own logs stay at DEBUG for debugging.
 * The production side is checked in GatewayProdProfileTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only"
})
class GatewayLaptopSettingsTest {

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("jwtpublickey", TestKeys::publicKeyText);
    }

    @Autowired
    WebTestClient client;

    @Autowired
    LoggingSystem loggingSystem;

    @Test
    void onTheLaptopActuatorIsStillOnTheMainPortAndLogsStayDebug() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        assertEquals(LogLevel.DEBUG, loggingSystem.getLoggerConfiguration("org.springframework.cloud.gateway")
                .getEffectiveLevel());
    }
}
