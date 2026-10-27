package com.example.APIGateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Does the gateway start? Safe to run any time (option B, 2026-10-19).
 * - Dummy secrets: your real ones are never needed.
 * - Eureka off: no real service registry is contacted.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only"
})
class ApiGatewayApplicationTests {

    // Auth v2: a throwaway RSA public key made in memory (TestKeys), instead of the old dummy secret.
    @DynamicPropertySource
    static void testPublicKey(DynamicPropertyRegistry registry) {
        registry.add("jwtpublickey", TestKeys::publicKeyText);
    }

    @Test
    void contextLoads() {
    }
}
