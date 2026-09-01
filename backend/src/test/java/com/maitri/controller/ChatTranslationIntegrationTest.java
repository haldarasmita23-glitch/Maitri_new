package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.user.LanguagePreferenceRequest;
import com.maitri.model.*;
import com.maitri.repository.ChatRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("Chat Multilingual Translation Integration Tests")
class ChatTranslationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User englishUser;
    private User kannadaVendorUser;
    private User hindiUser;
    private Vendor kannadaVendor;

    private String englishUserToken;
    private String kannadaVendorToken;
    private String hindiUserToken;

    @BeforeEach
    void setUp() {
        chatRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();

        // 1. English User
        englishUser = userRepository.save(User.builder()
                .name("Alice Resident")
                .email("alice@maitri.test")
                .password(passwordEncoder.encode("Password@123"))
                .role(Role.USER)
                .preferredLanguage("en")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // 2. Kannada Vendor User & Vendor Document
        kannadaVendorUser = userRepository.save(User.builder()
                .name("Ravi Tailor")
                .email("ravi.vendor@maitri.test")
                .password(passwordEncoder.encode("Password@123"))
                .role(Role.VENDOR)
                .preferredLanguage("kn")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        kannadaVendor = vendorRepository.save(Vendor.builder()
                .userId(kannadaVendorUser.getId())
                .shopName("Ravi Tailoring Peenya")
                .ownerName("Ravi Kumar")
                .address("1st Cross, Peenya 2nd Stage")
                .area("Peenya")
                .status(VendorStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        // 3. Hindi User
        hindiUser = userRepository.save(User.builder()
                .name("Suresh Gupta")
                .email("suresh@maitri.test")
                .password(passwordEncoder.encode("Password@123"))
                .role(Role.USER)
                .preferredLanguage("hi")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Generate tokens
        englishUserToken = "Bearer " + jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        englishUser.getEmail(), englishUser.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        kannadaVendorToken = "Bearer " + jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        kannadaVendorUser.getEmail(), kannadaVendorUser.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))));

        hindiUserToken = "Bearer " + jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        hindiUser.getEmail(), hindiUser.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Nested
    @DisplayName("End-to-End Chat Translation Flow")
    class ChatTranslationFlow {

        @Test
        @DisplayName("User (EN) sends message to Vendor (KN) -> Translated to Kannada")
        void englishUserToKannadaVendor() throws Exception {
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .message("Your order is ready")
                    .messageType(MessageType.TEXT)
                    .build();

            mockMvc.perform(post("/api/chats/{chatId}/messages", kannadaVendorUser.getId())
                            .header("Authorization", englishUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.originalMessage").value("Your order is ready"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ"))
                    .andExpect(jsonPath("$.data.sourceLanguage").value("en"))
                    .andExpect(jsonPath("$.data.targetLanguage").value("kn"))
                    .andExpect(jsonPath("$.data.translationStatus").value("TRANSLATED"))
                    .andExpect(jsonPath("$.data.isOwnMessage").value(true));

            // Verify stored in MongoDB
            List<Chat> savedChats = chatRepository.findAll();
            assertThat(savedChats).hasSize(1);
            Chat saved = savedChats.get(0);
            assertThat(saved.getOriginalMessage()).isEqualTo("Your order is ready");
            assertThat(saved.getTranslatedMessage()).isEqualTo("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ");
            assertThat(saved.getSourceLanguage()).isEqualTo("en");
            assertThat(saved.getTargetLanguage()).isEqualTo("kn");
            assertThat(saved.getTranslationStatus()).isEqualTo(TranslationStatus.TRANSLATED);

            // Verify Vendor (Receiver) retrieves conversation and sees translated text as primary
            mockMvc.perform(get("/api/chats/{chatId}", englishUser.getId())
                            .header("Authorization", kannadaVendorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lastMessage").value("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ"))
                    .andExpect(jsonPath("$.data.originalMessage").value("Your order is ready"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ"))
                    .andExpect(jsonPath("$.data.lastMessageIsOwn").value(false));
        }

        @Test
        @DisplayName("Vendor (KN) sends message to User (EN) -> Translated to English")
        void kannadaVendorToEnglishUser() throws Exception {
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .message("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ")
                    .messageType(MessageType.TEXT)
                    .build();

            mockMvc.perform(post("/api/chats/{chatId}/messages", englishUser.getId())
                            .header("Authorization", kannadaVendorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.originalMessage").value("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("your order is ready"))
                    .andExpect(jsonPath("$.data.sourceLanguage").value("kn"))
                    .andExpect(jsonPath("$.data.targetLanguage").value("en"))
                    .andExpect(jsonPath("$.data.translationStatus").value("TRANSLATED"));

            // Verify User (Receiver) retrieves conversation and sees English translation
            mockMvc.perform(get("/api/chats/{chatId}", kannadaVendorUser.getId())
                            .header("Authorization", englishUserToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lastMessage").value("your order is ready"))
                    .andExpect(jsonPath("$.data.originalMessage").value("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("your order is ready"))
                    .andExpect(jsonPath("$.data.sourceLanguage").value("kn"))
                    .andExpect(jsonPath("$.data.targetLanguage").value("en"));
        }

        @Test
        @DisplayName("User (HI) sends Hindi message to Vendor (KN) -> Translated to Kannada")
        void hindiUserToKannadaVendor() throws Exception {
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .message("क्या आपकी दुकान आज खुली है?")
                    .messageType(MessageType.TEXT)
                    .build();

            mockMvc.perform(post("/api/chats/{chatId}/messages", kannadaVendorUser.getId())
                            .header("Authorization", hindiUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.originalMessage").value("क्या आपकी दुकान आज खुली है?"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ?"))
                    .andExpect(jsonPath("$.data.sourceLanguage").value("hi"))
                    .andExpect(jsonPath("$.data.targetLanguage").value("kn"))
                    .andExpect(jsonPath("$.data.translationStatus").value("TRANSLATED"));
        }

        @Test
        @DisplayName("User preference change updates future translation target")
        void userPreferenceChangeUpdatesTranslation() throws Exception {
            // Update English user preference to Hindi
            LanguagePreferenceRequest langReq = new LanguagePreferenceRequest("hi");
            mockMvc.perform(put("/api/users/preferences/language")
                            .header("Authorization", englishUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(langReq)))
                    .andExpect(status().isOk());

            // Vendor sends Kannada message -> now targets Hindi
            ChatMessageRequest request = ChatMessageRequest.builder()
                    .message("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ")
                    .messageType(MessageType.TEXT)
                    .build();

            mockMvc.perform(post("/api/chats/{chatId}/messages", englishUser.getId())
                            .header("Authorization", kannadaVendorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.targetLanguage").value("hi"))
                    .andExpect(jsonPath("$.data.translatedMessage").value("आपका ऑर्डर तैयार है"));
        }

        @Test
        @DisplayName("Legacy chat messages without translation fields render cleanly without crash")
        void legacyChatBackwardCompatibility() throws Exception {
            // Directly insert a legacy Chat document without translation fields
            Chat legacyChat = Chat.builder()
                    .senderId(kannadaVendorUser.getId())
                    .senderRole(Role.VENDOR)
                    .receiverId(englishUser.getId())
                    .receiverRole(Role.USER)
                    .message("Legacy text before Phase 14")
                    .messageType(MessageType.TEXT)
                    .timestamp(LocalDateTime.now())
                    .read(false)
                    .build();
            chatRepository.save(legacyChat);

            mockMvc.perform(get("/api/chats/{chatId}", kannadaVendorUser.getId())
                            .header("Authorization", englishUserToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lastMessage").value("Legacy text before Phase 14"))
                    .andExpect(jsonPath("$.data.originalMessage").value("Legacy text before Phase 14"));
        }
    }
}
