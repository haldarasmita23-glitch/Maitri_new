package com.maitri;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
 * ⚠️  IMPORTANT — PREREQUISITE:
 *   This test starts the full Spring Boot application context,
 *   which REQUIRES a running MongoDB instance on localhost:27017.
 *
 *   Before running tests:
 *   1. Make sure MongoDB is started on your machine.
 *   2. Make sure application-local.properties exists.
 *
 *   If MongoDB is not running, the test will fail with a connection error —
 *   this is expected and does NOT indicate a code problem.
 *
 * @SpringBootTest:
 *   Starts the full application context (all beans, security, MongoDB connection).
 *   This is an integration test, not a unit test.
 *
 * @ActiveProfiles("local"):
 *   Tells Spring to use application-local.properties during the test.
 */
@SpringBootTest
@ActiveProfiles("local")
class MaitriApplicationTests {

    /**
     * Context Loads Test
     *
     * This test has no assertions. It passes if — and only if — the
     * Spring application context loads without throwing an exception.
     *
     * If ANY of the following are wrong, this test will fail:
     *   - MongoDB is not running
     *   - A @Bean is misconfigured
     *   - A required property is missing
     *   - A class has a syntax error or missing import
     *
     * Think of this as the "does the engine start?" test.
     */
    @Test
    void contextLoads() {
        // No assertions needed — if we reach this line, the context loaded successfully.
    }
}
