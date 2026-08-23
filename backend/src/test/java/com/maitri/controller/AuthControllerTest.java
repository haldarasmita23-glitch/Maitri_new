package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.auth.LoginRequest;
import com.maitri.dto.auth.RegisterRequest;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController Integration Tests — Phase 3A
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   These are full-stack integration tests. They exercise:
 *     Controller → Service → Repository → Embedded MongoDB (Flapdoodle)
 *
 *   Flapdoodle starts an embedded MongoDB automatically on test startup.
 *   No external MongoDB is required.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  Successful user registration (201 + JWT)
 *   2.  Duplicate email registration (409 Conflict)
 *   3.  Password hashing — raw password NOT stored, BCrypt hash IS stored
 *   4.  Successful login (200 + JWT)
 *   5.  Login with incorrect password (401)
 *   6.  JWT generation — token is non-blank in response
 *   7.  Protected endpoint WITHOUT JWT → 401 Unauthorized
 *   8.  Protected endpoint WITH valid JWT → 200 OK
 *   9.  Protected endpoint WITH invalid JWT → 401 Unauthorized
 *   10. Public health endpoint (200 — no JWT needed)
 *   11. ADMIN/SUPER_ADMIN role blocked at public registration (403 Forbidden)
 *
 * @SpringBootTest: Loads the full application context.
 * @AutoConfigureMockMvc: Creates a MockMvc instance without starting a real server.
 * @TestPropertySource: Uses test properties (Flapdoodle config, JWT, admin seed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("AuthController Integration Tests — Phase 3A")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String REGISTER_URL  = "/api/auth/register";
    private static final String LOGIN_URL      = "/api/auth/login";
    private static final String ME_URL         = "/api/auth/me";
    private static final String HEALTH_URL     = "/api/health";

    private static final String TEST_NAME      = "Test User";
    private static final String TEST_EMAIL     = "testuser@maitri.test";
    private static final String TEST_PASSWORD  = "Password@123";

    // ─── Setup ────────────────────────────────────────────────────────────────

    /**
     * Cleans the users collection before each test to ensure test isolation.
     * DataSeeder runs on context startup and may have created an admin —
     * we preserve isolation by clearing before each test.
     */
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 1 — Successful Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. POST /api/auth/register — success: 201 with JWT and user info")
    void register_success_returns201WithTokenAndUserInfo() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful."))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.user.name").value(TEST_NAME))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.active").value(true))
                // CRITICAL: password must NEVER appear in any response
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").isNotEmpty()); // token must be present

        // Verify user is actually in MongoDB
        assertThat(userRepository.findByEmail(TEST_EMAIL)).isPresent();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 2 — Duplicate Email Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. POST /api/auth/register — duplicate email: 409 Conflict")
    void register_duplicateEmail_returns409Conflict() throws Exception {

        // Pre-seed a user with the same email
        seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        RegisterRequest request = RegisterRequest.builder()
                .name("Another User")
                .email(TEST_EMAIL)
                .password("AnotherPass@123")
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 3 — Password Hashing
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. Registration — password is BCrypt-hashed, never stored as plaintext")
    void register_passwordIsHashed_notStoredAsPlaintext() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(TEST_EMAIL).orElseThrow();

        // Raw password must NOT equal the stored value
        assertThat(saved.getPassword()).isNotEqualTo(TEST_PASSWORD);

        // Stored value must start with BCrypt prefix $2a$ or $2b$
        assertThat(saved.getPassword()).startsWith("$2");

        // BCrypt verify: raw password matches hash
        assertThat(passwordEncoder.matches(TEST_PASSWORD, saved.getPassword())).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 4 — Successful Login
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4. POST /api/auth/login — success: 200 with JWT and user info")
    void login_success_returns200WithTokenAndUserInfo() throws Exception {

        seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful."))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                // password NEVER in response
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 5 — Incorrect Password Login
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5. POST /api/auth/login — wrong password: 401 Unauthorized")
    void login_incorrectPassword_returns401() throws Exception {

        seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password("WrongPassword@999")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Generic message — must NOT reveal whether email or password was wrong
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 6 — JWT Token Generation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. JWT token — generated on login, contains valid email claim")
    void login_jwtTokenIsGeneratedAndContainsEmailClaim() throws Exception {

        seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        String responseBody = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the token from the response
        String token = objectMapper.readTree(responseBody)
                .path("data")
                .path("token")
                .asText();

        // Token must be non-empty
        assertThat(token).isNotBlank();

        // JwtService must be able to extract the email from the token
        String extractedEmail = jwtService.extractEmail(token);
        assertThat(extractedEmail).isEqualTo(TEST_EMAIL);

        // JwtService must confirm token is valid for the user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("doesn't matter for validation")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 7 — Protected Endpoint WITHOUT JWT
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("7. GET /api/auth/me — no JWT: 401 Unauthorized")
    void getMe_withoutJwt_returns401() throws Exception {

        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 8 — Protected Endpoint WITH valid JWT
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("8. GET /api/auth/me — valid JWT: 200 with user profile (no password)")
    void getMe_withValidJwt_returns200WithUserProfile() throws Exception {

        User user = seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        // Generate a valid JWT for this user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.name").value(TEST_NAME))
                .andExpect(jsonPath("$.data.role").value("USER"))
                // password NEVER in response
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 9 — Protected Endpoint WITH invalid JWT
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("9. GET /api/auth/me — invalid JWT: 401 Unauthorized")
    void getMe_withInvalidJwt_returns401() throws Exception {

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 10 — Public Health Endpoint
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("10. GET /api/health — public endpoint: 200 without JWT")
    void healthEndpoint_isPublicAndReturns200() throws Exception {

        mockMvc.perform(get(HEALTH_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 11 — Prevent ADMIN/SUPER_ADMIN via Public Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("11a. POST /api/auth/register — ADMIN role: 403 Forbidden")
    void register_withAdminRole_returns403Forbidden() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name("Hacker")
                .email("hacker@maitri.test")
                .password("Hacker@Pass123")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("11b. POST /api/auth/register — SUPER_ADMIN role: 403 Forbidden")
    void register_withSuperAdminRole_returns403Forbidden() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name("Super Hacker")
                .email("superhacker@maitri.test")
                .password("SuperHacker@Pass123")
                .role(Role.SUPER_ADMIN)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BONUS — Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("12. POST /api/auth/register — blank name: 400 Bad Request")
    void register_blankName_returns400() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name("")
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("13. POST /api/auth/register — invalid email format: 400 Bad Request")
    void register_invalidEmail_returns400() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name(TEST_NAME)
                .email("not-an-email")
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("14. POST /api/auth/register — password too short: 400 Bad Request")
    void register_passwordTooShort_returns400() throws Exception {

        RegisterRequest request = RegisterRequest.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .password("short")
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

@Test
    @DisplayName("15. POST /api/auth/login — non-existent email: 401 Unauthorized")
    void login_nonExistentEmail_returns401() throws Exception {

        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@maitri.test")
                .password(TEST_PASSWORD)
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                // Generic message — does NOT reveal that the email doesn't exist
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // AUTHENTICATION INTEGRATION TESTS
    // ═════════════════════════════════════════════════════════════════════════════════════════════

@Test
    @DisplayName("Malformed JWT token: 401 Unauthorized")
    void getMe_withMalformedJwt_returns401() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Missing Bearer prefix: 401 Unauthorized")
    void getMe_withoutBearerPrefix_returns401() throws Exception {
        User user = seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        String token = jwtService.generateToken(userDetails);

        // Missing "Bearer " prefix
        mockMvc.perform(get(ME_URL)
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("JWT with unknown user: 401 Unauthorized")
    void getMe_withUnknownUserInJwt_returns401() throws Exception {
        // Generate token for a user that doesn't exist in DB
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("unknown@maitri.test")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Invalid JWT signature: 401 Unauthorized")
    void getMe_withInvalidSignature_returns401() throws Exception {
        User user = seedUser(TEST_EMAIL, TEST_PASSWORD, Role.USER);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        String validToken = jwtService.generateToken(userDetails);

        // Tamper the signature part (third segment)
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // Private Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Seeds a test user directly into MongoDB (bypasses the API layer).
     * Hashes the password with BCrypt before storage.
     *
     * @param email    User's email
     * @param password Raw (plaintext) password — will be hashed
     * @param role     User's role
     * @return The saved User document
     */
    private User seedUser(String email, String password, Role role) {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .name(TEST_NAME)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return userRepository.save(user);
    }

    /**
     * Creates a Spring Security UserDetails for the given email and role.
     * Used for generating JWT tokens in tests.
     */
    private UserDetails createUserDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("dummy")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .build();
    }
}
