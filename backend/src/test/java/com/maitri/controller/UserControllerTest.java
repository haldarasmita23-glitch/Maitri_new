package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.user.UserUpdateRequest;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.UserLocation;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController Integration Tests — Phase 6 (User Module)
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). The editable profile endpoint /api/users/me is open
 *   to USER and ADMIN only — VENDOR is denied per the SECURITY.md matrix.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  GET /me — USER: 200 with full safe profile (defaults resolved)
 *   2.  GET /me — no JWT: 401
 *   3.  GET /me — VENDOR token: 403
 *   4.  GET /me — ADMIN token: 200
 *   5.  GET /me — password is never returned
 *   6.  PUT /me — USER: 200, all editable fields applied
 *   7.  PUT /me — changes are persisted to MongoDB
 *   8.  PUT /me — blank name: 400
 *   9.  PUT /me — invalid phone: 400
 *   10. PUT /me — no JWT: 401
 *   11. PUT /me — VENDOR token: 403
 *   12. PUT /me — smuggled role/active/email ignored (no privilege escalation)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("UserController Integration Tests — Phase 6")
class UserControllerTest {

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

    private static final String USERS_URL = "/api/users";

    private static final String TEST_NAME    = "Test User";
    private static final String USER_EMAIL   = "user@maitri.test";
    private static final String VENDOR_EMAIL = "vendor@maitri.test";
    private static final String ADMIN_EMAIL  = "admin@maitri.test";

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/users/me
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. GET /api/users/me — USER: 200 with full safe profile")
    void getMe_withUserRole_returns200() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(get(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User profile retrieved."))
                .andExpect(jsonPath("$.data.name").value(TEST_NAME))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("2. GET /api/users/me — no JWT: 401 Unauthorized")
    void getMe_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(USERS_URL + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. GET /api/users/me — VENDOR token: 403 Forbidden")
    void getMe_withVendorRole_returns403() throws Exception {
        seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        mockMvc.perform(get(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. GET /api/users/me — ADMIN token: 200")
    void getMe_withAdminRole_returns200() throws Exception {
        seedUser(ADMIN_EMAIL, Role.ADMIN);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        mockMvc.perform(get(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("5. GET /api/users/me — password is never returned")
    void getMe_neverLeaksPassword() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(get(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUT /api/users/me
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. PUT /api/users/me — USER: 200, all editable fields applied")
    void updateMe_updatesOwnProfile_returns200() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User profile updated."))
                .andExpect(jsonPath("$.data.name").value("Kavitha Sharma"))
                .andExpect(jsonPath("$.data.phone").value("9845012345"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("kn"))
                .andExpect(jsonPath("$.data.location.area").value("Peenya"))
                .andExpect(jsonPath("$.data.location.city").value("Bengaluru"))
                .andExpect(jsonPath("$.data.profilePhoto").value("https://example.com/kavitha.jpg"))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("7. PUT /api/users/me — changes are persisted to MongoDB")
    void updateMe_persistsChanges() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isOk());

        User saved = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Kavitha Sharma");
        assertThat(saved.getPhone()).isEqualTo("9845012345");
        assertThat(saved.getPreferredLanguage()).isEqualTo("kn");
        assertThat(saved.getLocation()).isNotNull();
        assertThat(saved.getLocation().getArea()).isEqualTo("Peenya");
        assertThat(saved.getLocation().getCity()).isEqualTo("Bengaluru");
        assertThat(saved.getProfilePhoto()).isEqualTo("https://example.com/kavitha.jpg");
        assertThat(saved.getEmail()).isEqualTo(USER_EMAIL);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("8. PUT /api/users/me — blank name: 400 Bad Request")
    void updateMe_blankName_returns400() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        UserUpdateRequest request = validUpdateRequest();
        request.setName("");

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed. Please check your input."))
                .andExpect(jsonPath("$.errors", hasItem("Name is required.")));
    }

    @Test
    @DisplayName("9. PUT /api/users/me — invalid phone: 400 Bad Request")
    void updateMe_invalidPhone_returns400() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        UserUpdateRequest request = validUpdateRequest();
        request.setPhone("12345");

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("10. PUT /api/users/me — no JWT: 401 Unauthorized")
    void updateMe_withoutJwt_returns401() throws Exception {
        mockMvc.perform(put(USERS_URL + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. PUT /api/users/me — VENDOR token: 403 Forbidden")
    void updateMe_withVendorRole_returns403() throws Exception {
        seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("12. PUT /api/users/me — smuggled role/active/email ignored (no escalation)")
    void updateMe_cannotEscalatePrivileges() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        // Build a JSON body that smuggles in role/active/email alongside valid fields.
        UserUpdateRequest base = validUpdateRequest();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", base.getName());
        body.put("phone", base.getPhone());
        body.put("preferredLanguage", base.getPreferredLanguage());
        body.put("location", base.getLocation());
        body.put("profilePhoto", base.getProfilePhoto());
        body.put("role", "ADMIN");
        body.put("active", false);
        body.put("email", "hacked@maitri.test");

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL));

        User saved = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isActive()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Preferences: GET & PUT /api/users/preferences
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("13. GET /api/users/preferences — USER: 200 with default language")
    void getPreferences_returnsDefaultLanguage() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(get(USERS_URL + "/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.preferredLanguage").value("en"));
    }

    @Test
    @DisplayName("14. GET /api/users/preferences — without JWT: 401 Unauthorized")
    void getPreferences_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(USERS_URL + "/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15. PUT /api/users/preferences/language — USER updates language to Kannada (kn): 200")
    void updateLanguage_validCodeKannada_returns200() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(put(USERS_URL + "/preferences/language")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"kn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.preferredLanguage").value("kn"));

        User saved = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(saved.getPreferredLanguage()).isEqualTo("kn");
    }

    @Test
    @DisplayName("16. PUT /api/users/preferences/language — USER updates language to Hindi (hi): 200")
    void updateLanguage_validCodeHindi_returns200() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(put(USERS_URL + "/preferences/language")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.preferredLanguage").value("hi"));

        User saved = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(saved.getPreferredLanguage()).isEqualTo("hi");
    }

    @Test
    @DisplayName("17. PUT /api/users/preferences/language — invalid language code 'fr': 400 Bad Request")
    void updateLanguage_invalidCode_returns400() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        mockMvc.perform(put(USERS_URL + "/preferences/language")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("18. PUT /api/users/preferences/language — VENDOR token: 200 allowed")
    void updateLanguage_vendorRole_returns200() throws Exception {
        seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        mockMvc.perform(put(USERS_URL + "/preferences/language")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"kn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferredLanguage").value("kn"));
    }

    @Test
    @DisplayName("19. PUT /api/users/me — invalid language code 'de': 400 Bad Request")
    void updateMe_invalidLanguage_returns400() throws Exception {
        seedUser(USER_EMAIL, Role.USER);
        String token = tokenFor(USER_EMAIL, Role.USER);

        UserUpdateRequest request = validUpdateRequest();
        request.setPreferredLanguage("de");

        mockMvc.perform(put(USERS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    /** A valid update request body for tests. */
    private UserUpdateRequest validUpdateRequest() {
        return UserUpdateRequest.builder()
                .name("Kavitha Sharma")
                .phone("9845012345")
                .preferredLanguage("kn")
                .location(UserLocation.builder()
                        .area("Peenya")
                        .city("Bengaluru")
                        .build())
                .profilePhoto("https://example.com/kavitha.jpg")
                .build();
    }

    private User seedUser(String email, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder()
                .name(TEST_NAME)
                .email(email)
                .password(passwordEncoder.encode("Password@123"))
                .role(role)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * Returns a valid JWT for a user of the given role. Seeds the user only if
     * they don't already exist (email is unique, so we must not double-seed).
     */
    private String tokenFor(String email, Role role) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> seedUser(email, role));
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password("doesn't matter for validation")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .build();
        return jwtService.generateToken(userDetails);
    }
}
