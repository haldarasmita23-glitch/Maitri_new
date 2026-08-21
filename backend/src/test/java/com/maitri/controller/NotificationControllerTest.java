package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.notification.NotificationResponse;
import com.maitri.model.Category;
import com.maitri.model.Complaint;
import com.maitri.model.ComplaintStatus;
import com.maitri.model.Notification;
import com.maitri.model.NotificationType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.ComplaintRepository;
import com.maitri.repository.NotificationRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NotificationController Integration Tests — Phase 10 (Notifications Module).
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Tests the complete notification workflow including
 *   authorization, ownership isolation, unread count, mark-read, mark-all-read,
 *   and trigger wiring from vendor approval, complaint status change, review.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  GET /api/notifications — empty list for new user: 200 []
 *   2.  GET /api/notifications — returns own notifications newest first
 *   3.  GET /api/notifications — no JWT: 401
 *   4.  GET /api/notifications/unread-count — 200 with count
 *   5.  GET /api/notifications/unread-count — no JWT: 401
 *   6.  PUT /api/notifications/{id}/read — marks notification read: 200
 *   7.  PUT /api/notifications/{id}/read — idempotent on already-read: 200
 *   8.  PUT /api/notifications/{id}/read — unknown id: 404
 *   9.  PUT /api/notifications/{id}/read — other user's id: 404
 *   10. PUT /api/notifications/{id}/read — no JWT: 401
 *   11. PUT /api/notifications/read-all — marks all read: 200
 *   12. PUT /api/notifications/read-all — no unread: 200
 *   13. PUT /api/notifications/read-all — no JWT: 401
 *   14. VENDOR can list own notifications: 200
 *   15. ADMIN can list own notifications: 200
 *   16. No credentials leaked in response
 *   17. Trigger: Admin approves vendor → vendor owner gets VERIFICATION notification
 *   18. Trigger: Admin rejects vendor → vendor owner gets VERIFICATION notification
 *   19. Trigger: Vendor updates complaint status → complainant gets COMPLAINT notification
 *   20. Trigger: Admin updates complaint status → complainant gets COMPLAINT notification
 *   21. Trigger: User submits review → vendor owner gets REVIEW notification
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("NotificationController Integration Tests — Phase 10")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String NOTIFICATIONS_URL = "/api/notifications";

    // ─── Test Data ────────────────────────────────────────────────────────────

    private User testUser;
    private User anotherUser;
    private User adminUser;
    private User testVendor;
    private Vendor approvedVendor;
    private Category testCategory;
    private Complaint testComplaint;

    private String userToken;
    private String anotherUserToken;
    private String adminToken;
    private String vendorToken;

    @BeforeEach
    void setUp() {
        // Clean up
        notificationRepository.deleteAll();
        complaintRepository.deleteAll();
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

        // Create vendor profile linked to testVendor
        approvedVendor = vendorRepository.save(Vendor.builder()
                .userId(testVendor.getId())
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

        // Create a test complaint by testUser against approvedVendor
        testComplaint = complaintRepository.save(Complaint.builder()
                .userId(testUser.getId())
                .vendorId(approvedVendor.getId())
                .complaintType("QUALITY")
                .description("Test complaint")
                .status(ComplaintStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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

    // ─── Helper: create a notification directly ────────────────────────────────

    private Notification createNotification(User targetUser, NotificationType type) {
        return notificationRepository.save(Notification.builder()
                .userId(targetUser.getId())
                .userRole(targetUser.getRole())
                .type(type)
                .title("Test Title")
                .message("Test message")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // ─── Happy Path Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/notifications — USER: 200 with empty list for new user")
    void getNotifications_EmptyList() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Your notifications retrieved."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/notifications — USER: 200 with own notifications newest first")
    void getNotifications_Success() throws Exception {
        Notification n1 = createNotification(testUser, NotificationType.COMPLAINT);
        Thread.sleep(10); // ensure different timestamps for ordering
        Notification n2 = createNotification(testUser, NotificationType.REVIEW);

        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(n2.getId())) // newest first
                .andExpect(jsonPath("$.data[1].id").value(n1.getId()));
    }

    @Test
    @DisplayName("GET /api/notifications — no JWT: 401")
    void getNotifications_NoJwt() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count — USER: 200 with count")
    void getUnreadCount_Success() throws Exception {
        createNotification(testUser, NotificationType.COMPLAINT);
        createNotification(testUser, NotificationType.REVIEW);
        // One read
        notificationRepository.save(Notification.builder()
                .userId(testUser.getId())
                .userRole(Role.USER)
                .type(NotificationType.VERIFICATION)
                .title("Read")
                .message("Read message")
                .read(true)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get(NOTIFICATIONS_URL + "/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count — no JWT: 401")
    void getUnreadCount_NoJwt() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL + "/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read — USER: 200 marks notification read")
    void markAsRead_Success() throws Exception {
        Notification n = createNotification(testUser, NotificationType.COMPLAINT);

        mockMvc.perform(put(NOTIFICATIONS_URL + "/" + n.getId() + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification marked as read."))
                .andExpect(jsonPath("$.data.id").value(n.getId()))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read — idempotent on already-read")
    void markAsRead_Idempotent() throws Exception {
        Notification n = createNotification(testUser, NotificationType.COMPLAINT);
        n.setRead(true);
        notificationRepository.save(n);

        mockMvc.perform(put(NOTIFICATIONS_URL + "/" + n.getId() + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read — unknown id: 404")
    void markAsRead_UnknownId() throws Exception {
        mockMvc.perform(put(NOTIFICATIONS_URL + "/unknown-id/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read — other user's id: 404")
    void markAsRead_OtherUser() throws Exception {
        Notification n = createNotification(anotherUser, NotificationType.COMPLAINT);

        mockMvc.perform(put(NOTIFICATIONS_URL + "/" + n.getId() + "/read")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/notifications/{id}/read — no JWT: 401")
    void markAsRead_NoJwt() throws Exception {
        Notification n = createNotification(testUser, NotificationType.COMPLAINT);

        mockMvc.perform(put(NOTIFICATIONS_URL + "/" + n.getId() + "/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all — USER: 200 marks all read")
    void markAllAsRead_Success() throws Exception {
        createNotification(testUser, NotificationType.COMPLAINT);
        createNotification(testUser, NotificationType.REVIEW);
        createNotification(testUser, NotificationType.VERIFICATION);

        mockMvc.perform(put(NOTIFICATIONS_URL + "/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All notifications marked as read."));

        // Verify all are read
        mockMvc.perform(get(NOTIFICATIONS_URL + "/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all — no unread: 200 no-op")
    void markAllAsRead_NoUnread() throws Exception {
        // Only read notifications
        notificationRepository.save(Notification.builder()
                .userId(testUser.getId())
                .userRole(Role.USER)
                .type(NotificationType.COMPLAINT)
                .title("Read")
                .message("Read message")
                .read(true)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(put(NOTIFICATIONS_URL + "/read-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all — no JWT: 401")
    void markAllAsRead_NoJwt() throws Exception {
        mockMvc.perform(put(NOTIFICATIONS_URL + "/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications — VENDOR: 200 can list own notifications")
    void getNotifications_Vendor() throws Exception {
        createNotification(testVendor, NotificationType.VERIFICATION);

        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/notifications — ADMIN: 200 can list own notifications")
    void getNotifications_Admin() throws Exception {
        createNotification(adminUser, NotificationType.GENERAL);

        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Response contains no email/password credentials")
    void noCredentialsLeakage() throws Exception {
        createNotification(testUser, NotificationType.COMPLAINT);

        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    // ─── Trigger Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("TRIGGER: Admin approves vendor → vendor owner gets VERIFICATION notification")
    void trigger_AdminApprovesVendor() throws Exception {
        // Create a PENDING vendor linked to testVendor
        Vendor pendingVendor = vendorRepository.save(Vendor.builder()
                .userId(testVendor.getId())
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

        // Admin approves the vendor
        mockMvc.perform(patch("/api/vendors/" + pendingVendor.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Vendor owner checks notifications
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("VERIFICATION"))
                .andExpect(jsonPath("$.data[0].title").value("Vendor Approved"))
                .andExpect(jsonPath("$.data[0].message").value(
                        "Congratulations! Your business 'Pending Restaurant' has been approved and is now visible on Maitri."));
    }

    @Test
    @DisplayName("TRIGGER: Admin rejects vendor → vendor owner gets VERIFICATION notification")
    void trigger_AdminRejectsVendor() throws Exception {
        // Create a PENDING vendor linked to testVendor
        Vendor pendingVendor = vendorRepository.save(Vendor.builder()
                .userId(testVendor.getId())
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

        // Admin rejects the vendor
        mockMvc.perform(patch("/api/vendors/" + pendingVendor.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Vendor owner checks notifications
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("VERIFICATION"))
                .andExpect(jsonPath("$.data[0].title").value("Vendor Rejected"));
    }

    @Test
    @DisplayName("TRIGGER: Vendor updates complaint status → complainant gets COMPLAINT notification")
    void trigger_VendorUpdatesComplaintStatus() throws Exception {
        // Vendor updates complaint status from PENDING to IN_PROGRESS
        mockMvc.perform(patch("/api/complaints/" + testComplaint.getId() + "/status")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        // Complainant (testUser) checks notifications
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("COMPLAINT"))
                .andExpect(jsonPath("$.data[0].title").value("Complaint Status Updated"))
                .andExpect(jsonPath("$.data[0].message").value("Your complaint status has been updated to IN_PROGRESS."));
    }

    @Test
    @DisplayName("TRIGGER: Admin updates complaint status → complainant gets COMPLAINT notification")
    void trigger_AdminUpdatesComplaintStatus() throws Exception {
        // Admin updates complaint status from PENDING to RESOLVED
        mockMvc.perform(patch("/api/complaints/" + testComplaint.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        // Complainant (testUser) checks notifications
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("COMPLAINT"))
                .andExpect(jsonPath("$.data[0].title").value("Complaint Status Updated"))
                .andExpect(jsonPath("$.data[0].message").value("Your complaint status has been updated to RESOLVED."));
    }

    @Test
    @DisplayName("TRIGGER: User submits review → vendor owner gets REVIEW notification")
    void trigger_UserSubmitsReview() throws Exception {
        // User submits a review for the approved vendor
        String reviewPayload = String.format(
                "{\"vendorId\":\"%s\",\"rating\":5,\"reviewText\":\"Excellent service!\"}",
                approvedVendor.getId());

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isCreated());

        // Vendor owner checks notifications
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("REVIEW"))
                .andExpect(jsonPath("$.data[0].title").value("New Review Received"))
                .andExpect(jsonPath("$.data[0].message").value("Test User left a 5-star review for Test Restaurant."));
    }
}