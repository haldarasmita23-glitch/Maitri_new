package com.maitri.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService Unit Tests
 *
 * Tests JWT token generation, claim extraction, and validation.
 * These are pure unit tests — no Spring context, no MongoDB required.
 *
 * ReflectionTestUtils is used to inject test values into the @Value fields
 * (jwtSecret, jwtExpirationMs) without needing an application context.
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // Test-safe values — must be ≥ 32 chars for HS256
    private static final String TEST_SECRET =
            "jwt-unit-test-secret-key-at-least-32-chars-long-for-hs256";
    private static final long   TEST_EXPIRATION_MS = 3_600_000L; // 1 hour

    private static final String TEST_EMAIL = "jwttest@maitri.test";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    /**
     * Helper: builds a minimal UserDetails for test token generation.
     */
    private UserDetails buildUserDetails(String email, String role) {
        return User.builder()
                .username(email)
                .password("irrelevant-for-jwt")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .build();
    }

    // ─── Token Generation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken — returns non-blank JWT string")
    void generateToken_returnsNonBlankToken() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotBlank();
        // JWTs have exactly 3 dots-separated parts
        assertThat(token.split("\\.")).hasSize(3);
    }

    // ─── Claim Extraction ─────────────────────────────────────────────────────

    @Test
    @DisplayName("extractEmail — returns the email used to generate the token")
    void extractEmail_returnsCorrectEmail() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractEmail(token)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("extractRole — returns the role stored in the token (without ROLE_ prefix)")
    void extractRole_returnsRoleWithoutPrefix() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "ADMIN");
        String token = jwtService.generateToken(userDetails);

        // Role claim must NOT contain the ROLE_ prefix
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    // ─── Token Validation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid — returns true for a freshly generated token with matching user")
    void isTokenValid_validTokenAndMatchingUser_returnsTrue() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid — returns false when email in token doesn't match userDetails")
    void isTokenValid_differentEmail_returnsFalse() {
        // Token generated for TEST_EMAIL
        UserDetails tokenUser = buildUserDetails(TEST_EMAIL, "USER");
        String token = jwtService.generateToken(tokenUser);

        // Validate against a different user's UserDetails
        UserDetails differentUser = buildUserDetails("other@maitri.test", "USER");
        assertThat(jwtService.isTokenValid(token, differentUser)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid — returns false for a malformed / corrupted token")
    void isTokenValid_malformedToken_returnsFalse() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        assertThat(jwtService.isTokenValid("not.a.real.token", userDetails)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid — returns false for an expired token")
    void isTokenValid_expiredToken_returnsFalse() {
        // Set expiration to -1 ms (already expired)
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1L);

        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        String expiredToken = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(expiredToken, userDetails)).isFalse();
    }

    // ─── extractEmail on invalid token ────────────────────────────────────────

    @Test
    @DisplayName("extractEmail — throws JwtException for a tampered token")
    void extractEmail_tamperedToken_throwsJwtException() {
        UserDetails userDetails = buildUserDetails(TEST_EMAIL, "USER");
        String validToken = jwtService.generateToken(userDetails);

        // Tamper the payload part of the token (middle segment)
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + ".TAMPERED_PAYLOAD." + parts[2];

        assertThatThrownBy(() -> jwtService.extractEmail(tamperedToken))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
