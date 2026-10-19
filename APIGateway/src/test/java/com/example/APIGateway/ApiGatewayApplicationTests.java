package com.example.APIGateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does the gateway start? Safe to run any time (option B, 2026-10-19).
 * - Dummy secrets: your real ones are never needed.
 * - Eureka off: no real service registry is contacted.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "secretkey=dummy-test-only"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
