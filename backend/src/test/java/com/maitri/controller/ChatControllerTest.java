package com.maitri.controller;

import com.maitri.dto.chat.ChatCreateRequest;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.chat.ChatMessageResponse;
import com.maitri.dto.chat.ChatConversationResponse;
import com.maitri.dto.chat.UnreadCountResponse;
import com.maitri.exception.ChatNotFoundException;
import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("Chat Controller Integration Tests — Phase 11")
class ChatControllerTest {

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

    @Autowired
    private ChatService chatService;

    // Test constants
    private static final String CHAT_URL = "/api/chats";
    private static final String CHAT_ID_URL = "/api/chats/{chatId}";
    private static final String CHAT_MESSAGES_URL = "/api/chats/{chatId}/messages";
    private static final String CHAT_READ_URL = "/api/chats/{chatId}/read";
    private static final String CHAT_UNREAD_URL = "/api/chats/unread-count";

    private static final String TEST_USER_EMAIL = "testuser@maitri.test";
    private static final String TEST_VENDOR_EMAIL = "testvendor@maitri.test";
    private static final String TEST_PASSWORD = "Password@123";

    private User testUser;
    private User testVendor;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .name("Test User")
                .email(TEST_USER_EMAIL)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testVendor = User.builder()
                .name("Test Vendor")
                .email(TEST_VENDOR_EMAIL)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.VENDOR)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.deleteAll();
        userRepository.save(testUser);
        userRepository.save(testVendor);
    }

    @Test
    @DisplayName("GET /api/chats returns user's conversations")
    void getChats_returnsConversations() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/chats/{chatId} returns conversation when user is participant")
    void getChat_returnsConversation_whenUserIsParticipant() throws Exception {
        // First create a conversation
        ChatCreateRequest request = new ChatCreateRequest();
        request.setReceiverId(testVendor.getId());
        request.setReceiverRole("VENDOR");

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Now get the conversation
        mockMvc.perform(get(CHAT_ID_URL, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/chats starts a new conversation (USER→VENDOR)")
    void startConversation_userToVendor_createsMessage() throws Exception {
        ChatCreateRequest request = new ChatCreateRequest();
        request.setReceiverId(testVendor.getId());
        request.setReceiverRole("VENDOR");

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/chats starts a new conversation (VENDOR→USER)")
    void startConversation_vendorToUser_createsMessage() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_VENDOR_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_VENDOR")))
                .build();

        String token = jwtService.generateToken(userDetails);

        ChatCreateRequest request = new ChatCreateRequest();
        request.setReceiverId(testUser.getId());
        request.setReceiverRole("USER");

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/chats/{chatId}/messages sends a TEXT message")
    void sendMessage_textMessage() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("Hello there!");
        messageRequest.setMessageType(MessageType.TEXT);

        mockMvc.perform(post(CHAT_MESSAGES_URL, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Hello there!"))
                .andExpect(jsonPath("$.data.messageType").value("TEXT"));
    }

    @Test
    @DisplayName("POST /api/chats/{chatId}/messages sends an IMAGE message")
    void sendMessage_imageMessage() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage("https://example.com/image.jpg");
        messageRequest.setMessageType(MessageType.IMAGE);

        mockMvc.perform(post(CHAT_MESSAGES_URL, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageRequest))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageType").value("IMAGE"));
    }

    @Test
    @DisplayName("PUT /api/chats/{chatId}/read marks conversation as read")
    void markChatRead_marksRead() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(put(CHAT_READ_URL, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/chats/unread-count returns unread count")
    void getUnreadCount_returnsCount() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get(CHAT_UNREAD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("USER can message VENDOR only (USER→USER blocked)")
    void userCanOnlyMessageVendor() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_USER_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String userToken = jwtService.generateToken(userDetails);

        UserDetails vendorDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_VENDOR_EMAIL)
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_VENDOR")))
                .build();

        String vendorToken = jwtService.generateToken(vendorDetails);

        // USER trying to message another USER should be blocked
        ChatCreateRequest userToUserRequest = new ChatCreateRequest();
        userToUserRequest.setReceiverId(testUser.getId());
        userToUserRequest.setReceiverRole("USER");

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userToUserRequest))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().forbidden());

        // VENDOR trying to message another VENDOR should be blocked
        ChatCreateRequest vendorToVendorRequest = new ChatCreateRequest();
        vendorToVendorRequest.setReceiverId(testVendor.getId());
        vendorToVendorRequest.setReceiverRole("VENDOR");

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vendorToVendorRequest))
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().forbidden());
    }

    @Test
    @DisplayName("ADMIN can participate in any conversation")
    void adminCanParticipateAnyConversation() throws Exception {
        UserDetails adminDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin@maitri.test")
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();

        String adminToken = jwtService.generateToken(adminDetails);

        // ADMIN should be able to create conversations with anyone
        ChatCreateRequest adminRequest = new ChatCreateRequest();
        adminRequest.setReceiverId(testVendor.getId());
        adminRequest.setReceiverRole("VENDOR");

        mockMvc.perform(post(CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Non-participant cannot access another user's conversation")
    void nonParticipantCannotAccessConversation() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("other@maitri.test")
                .password(TEST_PASSWORD)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String userToken = jwtService.generateToken(userDetails);

        // Try to access a conversation that doesn't involve this user
        mockMvc.perform(get(CHAT_ID_URL, "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthenticated requests are rejected (401)")
    void unauthenticated_returns401() throws Exception {
        // No Authorization header should result in 401
        mockMvc.perform(get(CHAT_URL))
                .andExpect(status().isUnauthorized());
    }

    // Private helpers

    private User seedUser(String email, String password, Role role) {
        User user = User.builder()
                .name("Test User")
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }
}