package com.example.Eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does Eureka start? Safe to run any time (option B, 2026-10-19).
 * - No real port: the test starts the app without a web server.
 * - Peer address points nowhere (localhost:1), so a real Eureka on 8081 is never contacted.
 */
@SpringBootTest(properties = {
        "eureka.client.service-url.defaultZone=http://localhost:1/eureka/"
})
class EurekaApplicationTests {

    @Test
    void contextLoads() {
    }
}
