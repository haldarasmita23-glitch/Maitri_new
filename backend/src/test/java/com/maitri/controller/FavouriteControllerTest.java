package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.favourite.FavouriteRequest;
import com.maitri.model.Category;
import com.maitri.model.Favourite;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.FavouriteRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FavouriteController Integration Tests — Phase 8 (Favourites Module)
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Tests the complete favourite workflow including
 *   authorization, validation, business rules, and user isolation.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  GET /api/favourites — USER: 200 with the user's favourites
 *   2.  POST /api/favourites — USER: 201 adds an approved vendor
 *   3.  DELETE /api/favourites/{id} — USER: 200 removes the favourite
 *   4.  GET /api/favourites/{id} — USER: favourited = true
 *   5.  GET /api/favourites/{id} — USER: favourited = false
 *   6.  POST /api/favourites — missing vendorId: 400
 *   7.  GET /api/favourites — no JWT: 401
 *   8.  POST /api/favourites — no JWT: 401
 *   9.  DELETE /api/favourites/{id} — no JWT: 401
 *   10. GET /api/favourites/{id} — no JWT: 401
 *   11. All endpoints — VENDOR token: 403
 *   12. POST /api/favourites — ADMIN token: 201 (allowed)
 *   13. POST /api/favourites — duplicate favourite: 409
 *   14. POST /api/favourites — unknown vendor: 404
 *   15. POST /api/favourites — pending vendor: 404
 *   16. POST /api/favourites — rejected vendor: 404
 *   17. DELETE /api/favourites/{id} — not favourited: 404
 *   18. User isolation — user A cannot access/remove user B's favourite
 *   19. Unique compound index — persistence + duplicate-key protection
 *   20. Returned vendor data contains no sensitive credentials
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("FavouriteController Integration Tests — Phase 8")
class FavouriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FavouriteRepository favouriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String FAVOURITES_URL = "/api/favourites";

    // ─── Test Data ────────────────────────────────────────────────────────────

    private User testUser;
    private User anotherUser;
    private User adminUser;
    private User testVendor;
    private Vendor approvedVendor;
    private Vendor pendingVendor;
    private Vendor rejectedVendor;
    private Category testCategory;

    private String userToken;
    private String anotherUserToken;
    private String adminToken;
    private String vendorToken;

    @BeforeEach
    void setUp() {
        // Clean up
        favouriteRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create test category
        testCategory = categoryRepository.save(Category.builder()
                .categoryName("Street Food")
                .slug("street-food")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Create test users
        testUser = userRepository.save(User.builder()
                .name("Test User")
                .email("user@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        anotherUser = userRepository.save(User.builder()
                .name("Another User")
                .email("another@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Test Admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        testVendor = userRepository.save(User.builder()
                .name("Test Vendor")
                .email("vendor@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.VENDOR)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Create test vendors
        approvedVendor = vendorRepository.save(Vendor.builder()
                .userId("user123")  // Not linked to avoid user conflicts
                .shopName("Test Restaurant")
                .ownerName("Owner Name")
                .categoryId(testCategory.getId())
                .description("Great food")
                .address("Test Address")
                .area("Test Area")
                .phone("9876543210")
                .openingTime("09:00")
                .closingTime("22:00")
                .averageRating(0.0)
                .status(VendorStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        pendingVendor = vendorRepository.save(Vendor.builder()
                .userId("user456")
                .shopName("Pending Restaurant")
                .ownerName("Pending Owner")
                .categoryId(testCategory.getId())
                .description("Waiting for approval")
                .address("Pending Address")
                .area("Pending Area")
                .phone("9876543211")
                .openingTime("10:00")
                .closingTime("21:00")
                .averageRating(0.0)
                .status(VendorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        rejectedVendor = vendorRepository.save(Vendor.builder()
                .userId("user789")
                .shopName("Rejected Restaurant")
                .ownerName("Rejected Owner")
                .categoryId(testCategory.getId())
                .description("Was not approved")
                .address("Rejected Address")
                .area("Rejected Area")
                .phone("9876543212")
                .openingTime("10:00")
                .closingTime("21:00")
                .averageRating(0.0)
                .status(VendorStatus.REJECTED)
                .createdAt(LocalDateTime.now())
                .build());

        // Generate JWT tokens
        userToken = jwtService.generateToken(createUserDetails(testUser.getEmail(), "ROLE_USER"));
        anotherUserToken = jwtService.generateToken(createUserDetails(anotherUser.getEmail(), "ROLE_USER"));
        adminToken = jwtService.generateToken(createUserDetails(adminUser.getEmail(), "ROLE_ADMIN"));
        vendorToken = jwtService.generateToken(createUserDetails(testVendor.getEmail(), "ROLE_VENDOR"));
    }

    private UserDetails createUserDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("dummy")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }

    private FavouriteRequest request(String vendorId) {
        return FavouriteRequest.builder().vendorId(vendorId).build();
    }

    // ─── Happy Path Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/favourites — USER: 201 adds an approved vendor")
    void addFavourite_Success() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(approvedVendor.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor added to favourites."))
                .andExpect(jsonPath("$.data.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.data.vendorId").value(approvedVendor.getId()))
                .andExpect(jsonPath("$.data.vendor.shopName").value("Test Restaurant"));
    }

    @Test
    @DisplayName("GET /api/favourites — USER: 200 with the user's favourites")
    void getFavourites_Success() throws Exception {
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        mockMvc.perform(get(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].vendorId").value(approvedVendor.getId()))
                .andExpect(jsonPath("$.data[0].vendor.shopName").value("Test Restaurant"));
    }

    @Test
    @DisplayName("DELETE /api/favourites/{vendorId} — USER: 200 removes the favourite")
    void deleteFavourite_Success() throws Exception {
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        mockMvc.perform(delete(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor removed from favourites."));

        assertThat(favouriteRepository.countByUserId(testUser.getId())).isZero();
    }

    @Test
    @DisplayName("GET /api/favourites/{vendorId} — USER: favourited = true")
    void checkFavourite_True() throws Exception {
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        mockMvc.perform(get(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favourited").value(true));
    }

    @Test
    @DisplayName("GET /api/favourites/{vendorId} — USER: favourited = false")
    void checkFavourite_False() throws Exception {
        mockMvc.perform(get(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favourited").value(false));
    }

    // ─── Validation Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/favourites — missing vendorId: 400")
    void addFavourite_MissingVendorId_Returns400() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ─── Authentication Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/favourites — no JWT: 401")
    void getFavourites_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(FAVOURITES_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/favourites — no JWT: 401")
    void addFavourite_NoAuth_Returns401() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(approvedVendor.getId()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/favourites/{vendorId} — no JWT: 401")
    void deleteFavourite_NoAuth_Returns401() throws Exception {
        mockMvc.perform(delete(FAVOURITES_URL + "/" + approvedVendor.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/favourites/{vendorId} — no JWT: 401")
    void checkFavourite_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(FAVOURITES_URL + "/" + approvedVendor.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Authorization Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/favourites — VENDOR token: 403")
    void addFavourite_VendorRole_Returns403() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(approvedVendor.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/favourites — VENDOR token: 403")
    void getFavourites_VendorRole_Returns403() throws Exception {
        mockMvc.perform(get(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/favourites — ADMIN token: 201 (allowed)")
    void addFavourite_AdminRole_Allowed() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(approvedVendor.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── Business Rule Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/favourites — duplicate favourite: 409")
    void addFavourite_Duplicate_Returns409() throws Exception {
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(approvedVendor.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This vendor is already in your favourites."));
    }

    @Test
    @DisplayName("POST /api/favourites — unknown vendor: 404")
    void addFavourite_UnknownVendor_Returns404() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("unknown-vendor-id"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/favourites — pending vendor: 404")
    void addFavourite_PendingVendor_Returns404() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(pendingVendor.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/favourites — rejected vendor: 404")
    void addFavourite_RejectedVendor_Returns404() throws Exception {
        mockMvc.perform(post(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(rejectedVendor.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/favourites/{vendorId} — not favourited: 404")
    void deleteFavourite_NotFavourited_Returns404() throws Exception {
        mockMvc.perform(delete(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Vendor is not in your favourites."));
    }

    // ─── User Isolation Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("User isolation — user A cannot access or remove user B's favourite")
    void userIsolation_AnotherUsersFavourite_Untouched() throws Exception {
        // User A favourites the vendor
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        // User B's list must NOT contain it
        mockMvc.perform(get(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        // User B's check must be false
        mockMvc.perform(get(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favourited").value(false));

        // User B cannot delete it → 404
        mockMvc.perform(delete(FAVOURITES_URL + "/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isNotFound());

        // User A's favourite is untouched
        assertThat(favouriteRepository.existsByUserIdAndVendorId(
                testUser.getId(), approvedVendor.getId())).isTrue();
    }

    // ─── Database Index Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Unique compound index — persists favourite and rejects duplicate insert")
    void uniqueIndex_EnforcesOnePerUserPerVendor() {
        // Ensure the unique compound index {userId, vendorId} exists on the collection
        // (the embedded test Mongo does not always auto-create indexes eagerly).
        mongoTemplate.indexOps("favourites").ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).on("vendorId", Sort.Direction.ASC).unique());

        addFavouriteViaApi(approvedVendor.getId(), userToken);

        // Favourite is persisted for the correct user
        assertThat(favouriteRepository.countByUserId(testUser.getId())).isEqualTo(1);
        assertThat(favouriteRepository.findByUserIdAndVendorId(
                testUser.getId(), approvedVendor.getId())).isPresent();

        // A direct duplicate insert at repository level hits the unique index
        assertThrows(DuplicateKeyException.class, () ->
                favouriteRepository.save(Favourite.builder()
                        .userId(testUser.getId())
                        .vendorId(approvedVendor.getId())
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    // ─── Security / Data Projection Tests ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/favourites — returned vendor data contains no sensitive credentials")
    void getFavourites_NoSensitiveCredentials() throws Exception {
        addFavouriteViaApi(approvedVendor.getId(), userToken);

        mockMvc.perform(get(FAVOURITES_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vendor.shopName").value("Test Restaurant"))
                .andExpect(jsonPath("$.data[0].vendor.email").doesNotExist())
                .andExpect(jsonPath("$.data[0].vendor.password").doesNotExist());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addFavouriteViaApi(String vendorId, String token) {
        try {
            mockMvc.perform(post(FAVOURITES_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(vendorId))))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed favourite via API", e);
        }
    }
}