package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.category.CategoryRequest;
import com.maitri.model.Category;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.CategoryRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CategoryController Integration Tests — Phase 4
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). No external MongoDB required.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  GET /api/categories — public (no JWT) → 200, active categories returned
 *   2.  GET /api/categories — disabled categories are hidden
 *   3.  POST /api/categories — no JWT → 401
 *   4.  POST /api/categories — USER token → 403
 *   5.  POST /api/categories — VENDOR token → 403
 *   6.  POST /api/categories — ADMIN → 201 with full category data
 *   7.  POST /api/categories — duplicate name → 409
 *   8.  POST /api/categories — blank name → 400
 *   9.  POST /api/categories — auto-generated slug collision → 409
 *   10. PUT /api/categories/{id} — unknown id → 404
 *   11. PUT /api/categories/{id} — ADMIN update → 200, name updated
 *   12. PUT /api/categories/{id} — name used by another category → 409
 *   13. PATCH /api/categories/{id}/disable — → 200, active = false
 *   14. PATCH /api/categories/{id}/disable — unknown id → 404
 *   15. Disabled category disappears from the public GET list
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("CategoryController Integration Tests — Phase 4")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String CATEGORIES_URL = "/api/categories";

    private static final String TEST_NAME     = "Test User";
    private static final String TEST_EMAIL    = "testuser@maitri.test";
    private static final String ADMIN_EMAIL   = "admin@maitri.test";
    private static final String VENDOR_EMAIL  = "vendor@maitri.test";

    // ─── Setup ────────────────────────────────────────────────────────────────

    /**
     * Clears users AND categories before each test for isolation.
     * DataSeeder/CategorySeeder run on context startup — we reset both.
     */
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1 — Public GET
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. GET /api/categories — public (no JWT): 200 with active categories")
    void listCategories_noJwt_returns200WithCategories() throws Exception {
        seedCategory("Street Food", "street-food", true);
        seedCategory("Tailors", "tailors", true);

        mockMvc.perform(get(CATEGORIES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].slug", hasItems("street-food", "tailors")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2 — Disabled categories hidden
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. GET /api/categories — disabled categories are hidden")
    void listCategories_hidesDisabledCategories() throws Exception {
        seedCategory("Street Food", "street-food", true);
        seedCategory("Old Category", "old-cat", false);

        mockMvc.perform(get(CATEGORIES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slug").value("street-food"))
                .andExpect(jsonPath("$.data[0].slug").value(not("old-cat")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3-5 — Authorization (no JWT / non-admin roles)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. POST /api/categories — no JWT: 401 Unauthorized")
    void createCategory_withoutJwt_returns401() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. POST /api/categories — USER token: 403 Forbidden")
    void createCategory_withUserRole_returns403() throws Exception {
        String token = tokenFor(TEST_EMAIL, Role.USER);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. POST /api/categories — VENDOR token: 403 Forbidden")
    void createCategory_withVendorRole_returns403() throws Exception {
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6-9 — Create (ADMIN)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. POST /api/categories — ADMIN: 201 with full category data")
    void createCategory_withAdmin_returns201() throws Exception {
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .categoryImage("https://example.com/cafes.jpg")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category created."))
                .andExpect(jsonPath("$.data.categoryName").value("Cafes"))
                .andExpect(jsonPath("$.data.categoryImage").value("https://example.com/cafes.jpg"))
                .andExpect(jsonPath("$.data.slug").value("cafes"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    @DisplayName("7. POST /api/categories — duplicate name: 409 Conflict")
    void createCategory_duplicateName_returns409() throws Exception {
        seedCategory("Cafes", "cafes", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("8. POST /api/categories — blank name: 400 Bad Request")
    void createCategory_blankName_returns400() throws Exception {
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("9. POST /api/categories — auto-generated slug collision: 409 Conflict")
    void createCategory_slugCollision_returns409() throws Exception {
        // "Printing & Xerox" auto-slugifies to "printing-xerox"
        seedCategory("Printing & Xerox", "printing-xerox", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Printing Xerox")
                .build();

        mockMvc.perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 10-12 — Update (ADMIN)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("10. PUT /api/categories/{id} — unknown id: 404 Not Found")
    void updateCategory_unknownId_returns404() throws Exception {
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Cafes")
                .build();

        mockMvc.perform(put(CATEGORIES_URL + "/does-not-exist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("11. PUT /api/categories/{id} — ADMIN: 200, category updated")
    void updateCategory_withAdmin_returns200() throws Exception {
        Category existing = seedCategory("Cafes", "cafes", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Coffee Shops")
                .build();

        mockMvc.perform(put(CATEGORIES_URL + "/" + existing.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category updated."))
                .andExpect(jsonPath("$.data.categoryName").value("Coffee Shops"))
                .andExpect(jsonPath("$.data.slug").value("cafes"));
    }

    @Test
    @DisplayName("12. PUT /api/categories/{id} — name used by another category: 409 Conflict")
    void updateCategory_duplicateName_returns409() throws Exception {
        Category target = seedCategory("Cafes", "cafes", true);
        seedCategory("Tailors", "tailors", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Tailors")
                .build();

        mockMvc.perform(put(CATEGORIES_URL + "/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 13-15 — Disable (ADMIN)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("13. PATCH /api/categories/{id}/disable — ADMIN: 200, active = false")
    void disableCategory_withAdmin_returns200() throws Exception {
        Category existing = seedCategory("Cafes", "cafes", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        mockMvc.perform(patch(CATEGORIES_URL + "/" + existing.getId() + "/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category disabled."))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("14. PATCH /api/categories/{id}/disable — unknown id: 404 Not Found")
    void disableCategory_unknownId_returns404() throws Exception {
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        mockMvc.perform(patch(CATEGORIES_URL + "/does-not-exist/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("15. Disabled category disappears from the public GET list")
    void disableCategory_thenHiddenFromPublicGet() throws Exception {
        Category existing = seedCategory("Cafes", "cafes", true);
        String token = tokenFor(ADMIN_EMAIL, Role.ADMIN);

        mockMvc.perform(patch(CATEGORIES_URL + "/" + existing.getId() + "/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(CATEGORIES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Seeds a category directly into MongoDB (bypasses the API layer).
     */
    private Category seedCategory(String name, String slug, boolean active) {
        return categoryRepository.save(Category.builder()
                .categoryName(name)
                .slug(slug)
                .active(active)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Seeds a user with the given role and returns a valid JWT for them.
     * The user must exist in MongoDB because the JWT filter loads the user
     * from the database on every request.
     */
    private String tokenFor(String email, Role role) {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .name(TEST_NAME)
                .email(email)
                .password(passwordEncoder.encode("Password@123"))
                .role(role)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("doesn't matter for validation")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .build();
        return jwtService.generateToken(userDetails);
    }
}