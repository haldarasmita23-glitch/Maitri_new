package com.maitri;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Maitri Application — Spring Context Integration Test
 *
 * PURPOSE:
 *   Verifies that the entire Spring Boot application context loads
 *   successfully without errors. This catches configuration mistakes,
 *   missing beans, or wiring problems early.
 *
 * HOW TO RUN:
 *   Command: mvn test
 *   Or: Right-click this file in your IDE → Run
 *
 * NOTE — Phase 3A:
 *   This test now uses Flapdoodle embedded MongoDB (test scope dependency).
 *   No external MongoDB is required for this test to run.
 *   The embedded MongoDB is started automatically by Spring Boot Test.
 *
 * @SpringBootTest:
 *   Starts the full application context (all beans, security, MongoDB connection).
 *   This is an integration test, not a unit test.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class MaitriApplicationTests {

    /**
     * Context Loads Test
     *
     * This test has no assertions. It passes if — and only if — the
     * Spring application context loads without throwing an exception.
     *
     * If ANY of the following are wrong, this test will fail:
     *   - A @Bean is misconfigured
     *   - A required property is missing (jwt.secret, admin.initial.*)
     *   - A class has a syntax error or missing import
     *   - Circular dependencies exist
     */
    @Test
    void contextLoads() {
        // No assertions needed — if we reach this line, the context loaded successfully.
    }
}
