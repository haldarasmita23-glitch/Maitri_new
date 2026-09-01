package com.maitri.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.auth.LoginRequest;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private static final String ADMIN_EMAIL = "maitri.admin@gmail.com";
    private static final String ADMIN_PASS = "maitri@admin";

    @BeforeEach
    void setUp() {
        // Ensure admin user exists with BCrypt hashed password
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);

        User admin = User.builder()
                .name("Maitri Admin")
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASS))
                .role(Role.ADMIN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(admin);
    }

    @Test
    @DisplayName("Admin password in MongoDB is BCrypt-hashed and NOT stored as plaintext")
    void testAdminPasswordIsHashed() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getPassword()).isNotEqualTo(ADMIN_PASS);
        assertThat(admin.getPassword()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(ADMIN_PASS, admin.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Admin login with correct email and password returns JWT and ROLE_ADMIN")
    void testAdminLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_EMAIL, ADMIN_PASS);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email", is(ADMIN_EMAIL)))
                .andExpect(jsonPath("$.data.user.role", is("ADMIN")));
    }

    @Test
    @DisplayName("Admin login with wrong password fails with 401 Unauthorized")
    void testAdminLoginWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_EMAIL, "WrongPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /api/admin/users")
    void testAdminCanAccessAdminEndpoints() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_EMAIL, ADMIN_PASS);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("ROLE_USER cannot access admin endpoints (403 Forbidden)")
    void testUserCannotAccessAdminEndpoints() throws Exception {
        // Create normal user
        String userEmail = "customer.test@maitri.local";
        userRepository.findByEmail(userEmail).ifPresent(userRepository::delete);
        User user = User.builder()
                .name("Test Customer")
                .email(userEmail)
                .password(passwordEncoder.encode("CustomerPass123!"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        LoginRequest request = new LoginRequest(userEmail, "CustomerPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_VENDOR cannot access admin endpoints (403 Forbidden)")
    void testVendorCannotAccessAdminEndpoints() throws Exception {
        // Create vendor user
        String vendorEmail = "vendor.test@maitri.local";
        userRepository.findByEmail(vendorEmail).ifPresent(userRepository::delete);
        User vendorUser = User.builder()
                .name("Test Vendor Owner")
                .email(vendorEmail)
                .password(passwordEncoder.encode("VendorPass123!"))
                .role(Role.VENDOR)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(vendorUser);

        LoginRequest request = new LoginRequest(vendorEmail, "VendorPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can approve pending vendor application")
    void testAdminCanApproveVendor() throws Exception {
        // Create pending vendor
        Vendor pendingVendor = Vendor.builder()
                .userId("vendorUserPending1")
                .shopName("Pending Test Shop")
                .categoryId("cat1")
                .status(VendorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        Vendor saved = vendorRepository.save(pendingVendor);

        LoginRequest request = new LoginRequest(ADMIN_EMAIL, ADMIN_PASS);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(patch("/api/admin/vendors/" + saved.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        Vendor updated = vendorRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(VendorStatus.APPROVED);
    }
}
