package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.vendor.VendorApplyRequest;
import com.maitri.model.Category;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VendorController Integration Tests — Phase 5 (Vendor Module, Option A)
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Vendors are identified by users.role = VENDOR;
 *   the `vendors` collection holds only the business profile.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  Apply success → 201 (PENDING, rating 0, resolved category)
 *   2.  Apply requires authentication → 401
 *   3.  USER cannot create a vendor profile → 403
 *   4.  Duplicate profile → 409
 *   5.  Invalid (blank) shop name → 400
 *   6.  Unknown category → 404
 *   7.  Disabled category → 400
 *   8.  Public GET returns only APPROVED
 *   9.  Pending vendor hidden from public GET
 *   10. Rejected vendor hidden from public GET
 *   11. Search works (?q=)
 *   12. Category slug filtering works (?category=)
 *   13. Vendor detail works
 *   14. Unknown vendor → 404 (+ PENDING/REJECTED detail hidden)
 *   15. /me authentication/authorization (401 no JWT / 403 USER / 200 VENDOR)
 *   16. Vendor can update own profile
 *   17. Vendor cannot change approval status
 *   18. Non-admin cannot approve (403)
 *   19. ADMIN can approve → visible publicly
 *   20. ADMIN can reject → stays hidden
 *   21. Admin pending endpoint works
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("VendorController Integration Tests — Phase 5")
class VendorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String VENDORS_URL = "/api/vendors";

    private static final String TEST_NAME   = "Test User";
    private static final String VENDOR_EMAIL = "vendor@maitri.test";
    private static final String ADMIN_EMAIL  = "admin@maitri.test";
    private static final String USER_EMAIL   = "user@maitri.test";

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        vendorRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1-7 — Apply
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. POST /api/vendors/apply — VENDOR: 201, PENDING profile created")
    void apply_success_returns201() throws Exception {
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        Category cat = seedCategory("Street Food", "street-food", true);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("street-food"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor application submitted."))
                .andExpect(jsonPath("$.data.shopName").value("Shree Sagar Tiffin Centre"))
                .andExpect(jsonPath("$.data.ownerName").value("Ramesh Kumar"))
                .andExpect(jsonPath("$.data.categoryId").value(cat.getId()))
                .andExpect(jsonPath("$.data.categorySlug").value("street-food"))
                .andExpect(jsonPath("$.data.categoryName").value("Street Food"))
                .andExpect(jsonPath("$.data.averageRating").value(0.0))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    @DisplayName("2. POST /api/vendors/apply — no JWT: 401 Unauthorized")
    void apply_withoutJwt_returns401() throws Exception {
        seedCategory("Street Food", "street-food", true);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("street-food"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. POST /api/vendors/apply — USER token: 403 Forbidden")
    void apply_withUserRole_returns403() throws Exception {
        String token = tokenFor(USER_EMAIL, Role.USER);
        seedCategory("Street Food", "street-food", true);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("street-food"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. POST /api/vendors/apply — duplicate profile: 409 Conflict")
    void apply_duplicateProfile_returns409() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        Category cat = seedCategory("Street Food", "street-food", true);
        seedVendor(vendorUser, cat, "Existing Shop", VendorStatus.PENDING);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("street-food"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("5. POST /api/vendors/apply — blank shop name: 400 Bad Request")
    void apply_blankShopName_returns400() throws Exception {
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        seedCategory("Street Food", "street-food", true);

        VendorApplyRequest request = validApplyRequest("street-food");
        request.setShopName("");

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("6. POST /api/vendors/apply — unknown category: 404 Not Found")
    void apply_unknownCategory_returns404() throws Exception {
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("does-not-exist"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("7. POST /api/vendors/apply — disabled category: 400 Bad Request")
    void apply_disabledCategory_returns400() throws Exception {
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        seedCategory("Old Category", "old-cat", false);

        mockMvc.perform(post(VENDORS_URL + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApplyRequest("old-cat"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8-14 — Public browse & detail
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("8. GET /api/vendors — public: only APPROVED vendors returned")
    void listApproved_public_returnsOnlyApproved() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        User u2 = seedUser("v2@maitri.test", Role.VENDOR);
        User u3 = seedUser("v3@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Category repair = seedCategory("Repair", "repair", true);

        seedVendor(u1, food, "Shree Sagar", VendorStatus.APPROVED);
        seedVendor(u2, food, "Pending Shop", VendorStatus.PENDING);
        seedVendor(u3, repair, "Rejected Shop", VendorStatus.REJECTED);

        mockMvc.perform(get(VENDORS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shopName").value("Shree Sagar"))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("9. GET /api/vendors — pending vendor is hidden")
    void listApproved_pendingHidden() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        seedVendor(u1, food, "Pending Shop", VendorStatus.PENDING);

        mockMvc.perform(get(VENDORS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("10. GET /api/vendors — rejected vendor is hidden")
    void listApproved_rejectedHidden() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        seedVendor(u1, food, "Rejected Shop", VendorStatus.REJECTED);

        mockMvc.perform(get(VENDORS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("11. GET /api/vendors?q= — search works on shop name")
    void listApproved_searchWorks() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        User u2 = seedUser("v2@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Category repair = seedCategory("Repair", "repair", true);

        seedVendor(u1, food, "Shree Sagar Tiffin", VendorStatus.APPROVED);
        seedVendor(u2, repair, "TechFix Solutions", VendorStatus.APPROVED);

        mockMvc.perform(get(VENDORS_URL).param("q", "techfix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shopName").value("TechFix Solutions"));
    }

    @Test
    @DisplayName("12. GET /api/vendors?category= — slug filtering works")
    void listApproved_categorySlugFilter() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        User u2 = seedUser("v2@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Category tailors = seedCategory("Tailors", "tailors", true);

        seedVendor(u1, food, "Shree Sagar", VendorStatus.APPROVED);
        seedVendor(u2, tailors, "New Style Tailors", VendorStatus.APPROVED);

        mockMvc.perform(get(VENDORS_URL).param("category", "tailors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shopName").value("New Style Tailors"))
                .andExpect(jsonPath("$.data[0].categorySlug").value("tailors"));
    }

    @Test
    @DisplayName("13. GET /api/vendors/{id} — public: approved vendor detail")
    void getById_approvedVendor_returns200() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor v = seedVendor(u1, food, "Shree Sagar", VendorStatus.APPROVED);

        mockMvc.perform(get(VENDORS_URL + "/" + v.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shopName").value("Shree Sagar"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("14. GET /api/vendors/{id} — unknown vendor: 404; PENDING/REJECTED hidden")
    void getById_unknownAndNonApproved_returns404() throws Exception {
        User u1 = seedUser("v1@maitri.test", Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor pending = seedVendor(u1, food, "Pending Shop", VendorStatus.PENDING);

        mockMvc.perform(get(VENDORS_URL + "/does-not-exist"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(VENDORS_URL + "/" + pending.getId()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 15-17 — /me (VENDOR)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("15a. GET /api/vendors/me — no JWT: 401 Unauthorized")
    void getMe_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(VENDORS_URL + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15b. GET /api/vendors/me — USER token: 403 Forbidden")
    void getMe_withUserRole_returns403() throws Exception {
        String token = tokenFor(USER_EMAIL, Role.USER);
        mockMvc.perform(get(VENDORS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15c. GET /api/vendors/me — VENDOR token: 200 with own profile")
    void getMe_withVendorRole_returns200() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.PENDING);

        mockMvc.perform(get(VENDORS_URL + "/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shopName").value("Shree Sagar"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("16. PUT /api/vendors/me — VENDOR updates own profile, status preserved")
    void updateMe_updatesOwnProfile() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.PENDING);

        VendorApplyRequest update = validApplyRequest("street-food");
        update.setShopName("Shree Sagar Tiffin Centre 2");

        mockMvc.perform(put(VENDORS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor profile updated."))
                .andExpect(jsonPath("$.data.shopName").value("Shree Sagar Tiffin Centre 2"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.averageRating").value(0.0));
    }

    @Test
    @DisplayName("17. PUT /api/vendors/me — status field is ignored (cannot self-approve)")
    void updateMe_cannotChangeApprovalStatus() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String token = tokenFor(VENDOR_EMAIL, Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.PENDING);

        // Build a JSON body that smuggles in a "status": "APPROVED" field.
        VendorApplyRequest base = validApplyRequest("street-food");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shopName", base.getShopName());
        body.put("ownerName", base.getOwnerName());
        body.put("categoryId", base.getCategoryId());
        body.put("description", base.getDescription());
        body.put("address", base.getAddress());
        body.put("area", base.getArea());
        body.put("phone", base.getPhone());
        body.put("openingTime", base.getOpeningTime());
        body.put("closingTime", base.getClosingTime());
        body.put("status", "APPROVED");

        mockMvc.perform(put(VENDORS_URL + "/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 18-21 — Admin
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("18. PATCH approve — USER/VENDOR token: 403 Forbidden")
    void approve_nonAdmin_returns403() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor v = seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.PENDING);

        String userToken = tokenFor(USER_EMAIL, Role.USER);
        String vendorToken = tokenFor(VENDOR_EMAIL, Role.VENDOR);

        mockMvc.perform(patch(VENDORS_URL + "/" + v.getId() + "/approve")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(VENDORS_URL + "/" + v.getId() + "/approve")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("19. PATCH approve — ADMIN: 200, then vendor is publicly visible")
    void approve_withAdmin_returns200AndVisible() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String adminToken = tokenFor(ADMIN_EMAIL, Role.ADMIN);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor v = seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.PENDING);

        mockMvc.perform(patch(VENDORS_URL + "/" + v.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor approved."))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Now publicly visible
        mockMvc.perform(get(VENDORS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shopName").value("Shree Sagar"));
    }

    @Test
    @DisplayName("20. PATCH reject — ADMIN: 200, then vendor stays hidden")
    void reject_withAdmin_returns200AndHidden() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String adminToken = tokenFor(ADMIN_EMAIL, Role.ADMIN);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor v = seedVendor(vendorUser, food, "Shree Sagar", VendorStatus.APPROVED);

        mockMvc.perform(patch(VENDORS_URL + "/" + v.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor rejected."))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // Hidden from public
        mockMvc.perform(get(VENDORS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("21. GET /api/vendors/admin/pending — ADMIN: 200; USER: 403")
    void listPending_adminOnly() throws Exception {
        User vendorUser = seedUser(VENDOR_EMAIL, Role.VENDOR);
        String adminToken = tokenFor(ADMIN_EMAIL, Role.ADMIN);
        String userToken = tokenFor(USER_EMAIL, Role.USER);
        Category food = seedCategory("Street Food", "street-food", true);
        Vendor v = seedVendor(vendorUser, food, "Pending Shop", VendorStatus.PENDING);

        mockMvc.perform(get(VENDORS_URL + "/admin/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shopName").value("Pending Shop"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(get(VENDORS_URL + "/admin/pending")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    /** A valid apply request body for tests. */
    private VendorApplyRequest validApplyRequest(String categorySlug) {
        return VendorApplyRequest.builder()
                .shopName("Shree Sagar Tiffin Centre")
                .ownerName("Ramesh Kumar")
                .categoryId(categorySlug)
                .description("Authentic South Indian breakfast and lunch in Peenya.")
                .address("Near Gate 2, Peenya Industrial Area, Bengaluru")
                .area("Peenya")
                .phone("9845012345")
                .openingTime("06:30")
                .closingTime("14:00")
                .images(List.of())
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

    private Category seedCategory(String name, String slug, boolean active) {
        return categoryRepository.save(Category.builder()
                .categoryName(name)
                .slug(slug)
                .active(active)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Vendor seedVendor(User user, Category category, String shopName, VendorStatus status) {
        return vendorRepository.save(Vendor.builder()
                .userId(user.getId())
                .shopName(shopName)
                .ownerName(user.getName())
                .categoryId(category.getId())
                .description("A test business description.")
                .address("Test address, Bengaluru")
                .area("Peenya")
                .phone("9845012345")
                .openingTime("09:00")
                .closingTime("18:00")
                .images(List.of())
                .averageRating(0.0)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build());
    }
}