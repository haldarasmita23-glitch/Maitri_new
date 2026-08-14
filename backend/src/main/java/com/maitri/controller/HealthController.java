package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health Check Controller
 *
 * PURPOSE:
 *   Provides a simple public endpoint to verify that the backend server
 *   is running and responding to requests.
 *
 * WHY THIS IS IMPORTANT:
 *   1. Immediate verification: After starting the server, you can call
 *      GET /api/health to confirm everything started correctly.
 *
 *   2. Production monitoring: When deployed to AWS, the Load Balancer (ALB)
 *      will call this endpoint regularly. If it returns 200 OK, traffic
 *      is routed to the server. If it fails, traffic is stopped.
 *
 *   3. Debugging: If the frontend cannot connect to the backend, the first
 *      thing to check is: does /api/health return 200?
 *
 * ENDPOINT:
 *   GET http://localhost:8080/api/health
 *   Access: Public (no authentication required)
 *
 * @RestController — Combines @Controller + @ResponseBody.
 *   Every method return value is automatically converted to JSON.
 *
 * @RequestMapping("/api") — All endpoints in this class start with /api.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Reads the active Spring profile from configuration.
     * In local dev: "local". In production: "prod".
     * The default "unknown" is a safety fallback.
     *
     * @Value: Injects a value from application.properties at runtime.
     */
    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    /**
     * Health Check Endpoint
     *
     * Returns a JSON response with:
     *   - status: "UP" — the server is running
     *   - service: application name
     *   - version: current application version
     *   - environment: which profile is active (local/prod)
     *   - timestamp: when this response was generated
     *
     * Example Response:
     * {
     *   "success": true,
     *   "message": "Maitri backend is running.",
     *   "data": {
     *     "status": "UP",
     *     "service": "Maitri Backend",
     *     "version": "1.0.0",
     *     "environment": "local",
     *     "timestamp": "2026-08-13T14:30:00"
     *   },
     *   "timestamp": "2026-08-13T14:30:00"
     * }
     *
     * @GetMapping("/health") — maps GET requests to /api/health to this method.
     * ResponseEntity<> — lets us control the HTTP status code (200, 400, 500, etc.)
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        // LinkedHashMap preserves insertion order in the JSON output
        Map<String, String> healthDetails = new LinkedHashMap<>();
        healthDetails.put("status", "UP");
        healthDetails.put("service", "Maitri Backend");
        healthDetails.put("version", "1.0.0");
        healthDetails.put("environment", activeProfile);
        healthDetails.put("timestamp",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // ResponseEntity.ok() returns HTTP 200 OK with the body
        return ResponseEntity.ok(
                ApiResponse.success("Maitri backend is running.", healthDetails)
        );
    }
}
