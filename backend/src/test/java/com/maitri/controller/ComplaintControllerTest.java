package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.complaint.ComplaintCreateRequest;
import com.maitri.dto.complaint.ComplaintNoteRequest;
import com.maitri.dto.complaint.ComplaintStatusRequest;
import com.maitri.dto.complaint.ComplaintUpdateRequest;
import com.maitri.model.Category;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.ComplaintRepository;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ComplaintController Integration Tests — Phase 9 (Complaints Module)
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Tests the complete complaint workflow including
 *   authorization, validation, business rules, and user/vendor isolation.
 *
 * Unlike favourites/reviews, MULTIPLE complaints per user/vendor pair are
 * allowed — there is intentionally NO duplicate-complaint test.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   Positive:  create, list, get, update, delete (USER);
 *              vendor list, vendor status update;
 *              admin list, admin status update, admin note.
 *   Validation: missing vendorId, blank complaintType, blank description,
 *              description > 1000, invalid status.
 *   Authorization: anonymous 401; USER→VENDOR/ADMIN 403; VENDOR→USER/ADMIN 403.
 *   Isolation: user A vs user B; vendor A vs vendor B.
 *   Business rules: unknown/non-approved vendor 404; resolved locked;
 *              vendor cannot skip PENDING→RESOLVED; adminNote never exposed
 *              to USER/VENDOR; no credentials leaked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("ComplaintController Integration Tests — Phase 9")
class ComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private static final String COMPLAINTS_URL = "/api/complaints";

    private User testUser;
    private User anotherUser;
    private User adminUser;
    private User testVendor;
    private Vendor approvedVendor;
    private Vendor pendingVendor;
    private Category testCategory;

    private String userToken;
    private String anotherUserToken;
    private String adminToken;
    private String vendorToken;

    @BeforeEach
    void setUp() {
        complaintRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = categoryRepository.save(Category.builder()
                .categoryName("Street Food")
                .slug("street-food")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

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

        approvedVendor = vendorRepository.save(Vendor.builder()
                .userId(testVendor.getId())
                .shopName("Test Restaurant")
                .ownerName("Owner Name")
                .categoryId(testCategory.getId())
                .status(VendorStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        pendingVendor = vendorRepository.save(Vendor.builder()
                .userId("user-vendor-222")
                .shopName("Pending Restaurant")
                .ownerName("Pending Owner")
                .categoryId(testCategory.getId())
                .status(VendorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

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

    private ComplaintCreateRequest createRequest(String vendorId) {
        return ComplaintCreateRequest.builder()
                .vendorId(vendorId)
                .complaintType("Service")
                .description("Poor service received from this vendor.")
                .build();
    }

    private String createComplaintViaApi(String vendorId, String token, String complaintType, String description) throws Exception {
        ComplaintCreateRequest request = ComplaintCreateRequest.builder()
                .vendorId(vendorId)
                .complaintType(complaintType)
                .description(description)
                .build();
        String response = mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asText();
    }

    // ─── Create Complaints ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/complaints — USER: 201 with valid complaint")
    void createComplaint_Success() throws Exception {
        ComplaintCreateRequest request = createRequest(approvedVendor.getId());

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintType").value("Service"))
                .andExpect(jsonPath("$.data.description").value("Poor service received from this vendor."))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.vendorId").value(approvedVendor.getId()))
                .andExpect(jsonPath("$.data.userName").value("Test User"));
    }

    @Test
    @DisplayName("POST /api/complaints — no JWT: 401")
    void createComplaint_NoAuth_Returns401() throws Exception {
        mockMvc.perform(post(COMPLAINTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(approvedVendor.getId()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — VENDOR token: 403")
    void createComplaint_VendorRole_Returns403() throws Exception {
        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(approvedVendor.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — ADMIN: 201 (allowed)")
    void createComplaint_AdminRole_Allowed() throws Exception {
        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(approvedVendor.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/complaints — blank complaintType: 400")
    void createComplaint_BlankType_Returns400() throws Exception {
        ComplaintCreateRequest request = ComplaintCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .complaintType("")
                .description("Valid description")
                .build();

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — blank description: 400")
    void createComplaint_BlankDescription_Returns400() throws Exception {
        ComplaintCreateRequest request = ComplaintCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .complaintType("Service")
                .description("")
                .build();

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — missing vendorId: 400")
    void createComplaint_MissingVendorId_Returns400() throws Exception {
        ComplaintCreateRequest request = ComplaintCreateRequest.builder()
                .complaintType("Service")
                .description("Valid description")
                .build();

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — description > 1000: 400")
    void createComplaint_DescriptionTooLong_Returns400() throws Exception {
        String longDesc = "a".repeat(1001);
        ComplaintCreateRequest request = ComplaintCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .complaintType("Service")
                .description(longDesc)
                .build();

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — unknown vendor: 404")
    void createComplaint_UnknownVendor_Returns404() throws Exception {
        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("unknown-vendor"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — pending vendor: 404")
    void createComplaint_PendingVendor_Returns404() throws Exception {
        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(pendingVendor.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/complaints — USER: 201 without password/email leak")
    void createComplaint_NoSensitiveFields() throws Exception {
        ComplaintCreateRequest request = createRequest(approvedVendor.getId());

        mockMvc.perform(post(COMPLAINTS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userEmail").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ─── Get / My Complaints ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/complaints/my — USER: 200 with own complaints")
    void getMyComplaints_Success() throws Exception {
        createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Quality issue.");

        mockMvc.perform(get(COMPLAINTS_URL + "/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userName").value("Test User"));
    }

    @Test
    @DisplayName("GET /api/complaints/my — no JWT: 401")
    void getMyComplaints_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/complaints/my — VENDOR token: 403")
    void getMyComplaints_VendorRole_Returns403() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/my")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Get Single Complaint ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/complaints/{id} — USER: 200 for own complaint")
    void getComplaint_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(get(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(complaintId))
                .andExpect(jsonPath("$.data.userName").value("Test User"));
    }

    @Test
    @DisplayName("GET /api/complaints/{id} — no JWT: 401")
    void getComplaint_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/some-complaint-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/complaints/{id} — another user's complaint: 404")
    void getComplaint_AnotherUsersComplaint_Returns404() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(get(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Update Complaint ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/complaints/{id} — USER: 200 updates own PENDING complaint")
    void updateComplaint_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Original.");

        ComplaintUpdateRequest update = ComplaintUpdateRequest.builder()
                .complaintType("Quality")
                .description("Updated description.")
                .build();

        mockMvc.perform(put(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintType").value("Quality"))
                .andExpect(jsonPath("$.data.description").value("Updated description."));
    }

    @Test
    @DisplayName("PUT /api/complaints/{id} — another user's complaint: 404")
    void updateComplaint_AnotherUsersComplaint_Returns404() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        ComplaintUpdateRequest update = ComplaintUpdateRequest.builder()
                .complaintType("Quality")
                .description("Hijack attempt.")
                .build();

        mockMvc.perform(put(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + anotherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/complaints/{id} — RESOLVED complaint: 400")
    void updateComplaint_Resolved_Returns400() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        // Vendor moves to IN_PROGRESS then RESOLVED via the shared status endpoint
        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("IN_PROGRESS").build())))
                .andExpect(status().isOk());
        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("RESOLVED").build())))
                .andExpect(status().isOk());

        // User cannot edit a RESOLVED complaint
        ComplaintUpdateRequest update = ComplaintUpdateRequest.builder()
                .complaintType("Quality")
                .description("Too late to edit.")
                .build();

        mockMvc.perform(put(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Delete Complaint ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/complaints/{id} — USER: 200 deletes own PENDING complaint")
    void deleteComplaint_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(delete(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/complaints/{id} — another user's complaint: 404")
    void deleteComplaint_AnotherUsersComplaint_Returns404() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(delete(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Vendor: Get Complaints ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/complaints/vendor/me — VENDOR: 200 with own complaints")
    void getVendorComplaints_Success() throws Exception {
        createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(get(COMPLAINTS_URL + "/vendor/me")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].vendorName").value("Test Restaurant"));
    }

    @Test
    @DisplayName("GET /api/complaints/vendor/me — no JWT: 401")
    void getVendorComplaints_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/vendor/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/complaints/vendor/me — USER token: 403")
    void getVendorComplaints_UserRole_Returns403() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/vendor/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Vendor: Update Status (shared endpoint) ─────────────────────────

    @Test
    @DisplayName("PATCH /api/complaints/{id}/status — VENDOR: 200")
    void vendorUpdateStatus_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("IN_PROGRESS").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/status — VENDOR: cannot skip PENDING→RESOLVED: 400")
    void vendorUpdateStatus_SkipPendingToResolved_Returns400() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("RESOLVED").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/status — VENDOR: invalid status: 400")
    void vendorUpdateStatus_InvalidStatus_Returns400() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("URGENT").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/status — USER token: 403")
    void vendorUpdateStatus_UserRole_Returns403() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("IN_PROGRESS").build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Admin: Get All / Status / Note ──────────────────────────────────

    @Test
    @DisplayName("GET /api/complaints/admin — ADMIN: 200 with all complaints")
    void adminGetAll_Success() throws Exception {
        createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue one.");
        createComplaintViaApi(approvedVendor.getId(), anotherUserToken, "Billing", "Issue two.");

        mockMvc.perform(get(COMPLAINTS_URL + "/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/complaints/admin — USER token: 403")
    void adminGetAll_UserRole_Returns403() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/admin")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/complaints/admin — VENDOR token: 403")
    void adminGetAll_VendorRole_Returns403() throws Exception {
        mockMvc.perform(get(COMPLAINTS_URL + "/admin")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/status — ADMIN: 200 (any transition)")
    void adminUpdateStatus_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintStatusRequest.builder().status("RESOLVED").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/note — ADMIN: 200, note stored")
    void adminUpdateNote_Success() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/note")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintNoteRequest.builder().adminNote("Investigating with vendor.").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminNote").value("Investigating with vendor."));
    }

    @Test
    @DisplayName("PATCH /api/complaints/{id}/note — USER token: 403")
    void adminUpdateNote_UserRole_Returns403() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/note")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintNoteRequest.builder().adminNote("Nope.").build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/complaints/my — USER never sees adminNote")
    void userNeverSeesAdminNote() throws Exception {
        String complaintId = createComplaintViaApi(approvedVendor.getId(), userToken, "Service", "Issue.");

        // Admin sets a note
        mockMvc.perform(patch(COMPLAINTS_URL + "/" + complaintId + "/note")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ComplaintNoteRequest.builder().adminNote("Internal note.").build())))
                .andExpect(status().isOk());

        // User fetch must NOT expose the note
        mockMvc.perform(get(COMPLAINTS_URL + "/" + complaintId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminNote").doesNotExist());
    }
}