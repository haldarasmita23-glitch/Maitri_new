package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.chat.ChatCreateRequest;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.chat.ChatMessageResponse;
import com.maitri.dto.chat.ChatConversationResponse;
import com.maitri.dto.chat.UnreadCountResponse;
import com.maitri.exception.ChatAccessDeniedException;
import com.maitri.exception.ChatNotFoundException;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * Chat Controller — Phase 11 (Chat / Contact Module).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET    /api/chats                       — USER|VENDOR|ADMIN: list conversations
 *   POST   /api/chats                       — USER|VENDOR|ADMIN: start conversation
 *   GET    /api/chats/{chatId}              — USER|VENDOR|ADMIN: get conversation
 *   POST   /api/chats/{chatId}/messages     — USER|VENDOR|ADMIN: send message
 *   PUT    /api/chats/{chatId}/read         — USER|VENDOR|ADMIN: mark as read
 *   GET    /api/chats/unread-count          — USER|VENDOR|ADMIN: unread count
 *
 * ─── MESSAGING RULES ─────────────────────────────────────────────────────────
 *   - USER can only message VENDOR (not another USER)
 *   - VENDOR can only message USER (not another VENDOR)
 *   - ADMIN can message anyone
 *
 * All responses follow the standard ApiResponse<T> wrapper pattern.
 */
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // ─── Helper: extract the caller's role from authentication ────────────────
    private Role extractRole(Authentication auth) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        for (GrantedAuthority a : authorities) {
            String r = a.getAuthority();
            if ("ROLE_ADMIN".equals(r))  return Role.ADMIN;
            if ("ROLE_VENDOR".equals(r)) return Role.VENDOR;
        }
        return Role.USER;
    }

    // ─── Helper: resolve the caller's User entity from authentication ─────────
    private User resolveUser(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found: " + email));
    }

    /**
     * GET /api/chats
     * List authenticated user's conversations (newest activity first).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatConversationResponse>>> getChats(
            Authentication authentication) {
        List<ChatConversationResponse> conversations = chatService.getConversations(authentication);
        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved.", conversations));
    }

    /**
     * GET /api/chats/unread-count
     * Get total unread message count for the authenticated user.
     * NOTE: Must be declared BEFORE /{chatId} to avoid path ambiguity.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            Authentication authentication) {
        UnreadCountResponse count = chatService.getUnreadCount(authentication);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved.", count));
    }

    /**
     * GET /api/chats/{chatId}
     * Get a single conversation by partner/chat ID.
     * Returns 404 if user is not a participant.
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> getChat(
            @PathVariable String chatId,
            Authentication authentication) {
        ChatConversationResponse conversation = chatService.getConversation(authentication, chatId);
        return ResponseEntity.ok(ApiResponse.success("Conversation retrieved.", conversation));
    }

    /**
     * POST /api/chats
     * Start a new conversation.
     * - USER can only start a conversation with VENDOR
     * - VENDOR can only start a conversation with USER
     * - ADMIN can start a conversation with anyone
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatConversationResponse>> startConversation(
            @RequestBody @Valid ChatCreateRequest request,
            Authentication authentication) {

        Role callerRole = extractRole(authentication);
        Role receiverRole;

        try {
            receiverRole = Role.valueOf(request.getReceiverRole().trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid receiverRole. Must be USER, VENDOR, or ADMIN."));
        }

        // ── Enforce messaging rules ───────────────────────────────────────────
        if (callerRole == Role.VENDOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Vendors cannot initiate conversations. Only customers may send conversation requests."));
        }
        if (callerRole == Role.USER && receiverRole != Role.VENDOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Users may only message vendors."));
        }

        ChatConversationResponse conversation =
                chatService.startConversation(authentication, request.getReceiverId(), receiverRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Conversation started.", conversation));
    }

    /**
     * PUT /api/chats/{chatId}/accept
     * Accept a pending conversation request from a customer.
     * Accessible by VENDOR and ADMIN.
     */
    @PutMapping("/{chatId}/accept")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> acceptConversation(
            @PathVariable String chatId,
            Authentication authentication) {
        Role callerRole = extractRole(authentication);
        if (callerRole != Role.VENDOR && callerRole != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only vendors and administrators may accept conversation requests."));
        }
        ChatConversationResponse conversation = chatService.acceptConversation(authentication, chatId);
        return ResponseEntity.ok(ApiResponse.success("Conversation request accepted.", conversation));
    }

    /**
     * POST /api/chats/{chatId}/accept
     * Alias for accept conversation.
     */
    @PostMapping("/{chatId}/accept")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> acceptConversationPost(
            @PathVariable String chatId,
            Authentication authentication) {
        return acceptConversation(chatId, authentication);
    }

    /**
     * GET /api/chats/{chatId}/messages?page=0&size=30
     * Retrieve the full message thread between the authenticated user and the partner.
     *
     * Messages are returned newest-first (DB order). The frontend should reverse
     * the list so oldest messages appear at the top of the chat window.
     *
     * @param chatId         Partner's account ID (used as conversationId)
     * @param page           Zero-based page number (default: 0)
     * @param size           Page size, capped at 50 (default: 30)
     * @param authentication The authenticated caller
     */
    @GetMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getMessages(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {

        int cappedSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, cappedSize);
        Page<ChatMessageResponse> messages = chatService.getMessages(authentication, chatId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved.", messages));
    }

    /**
     * POST /api/chats/{chatId}/messages
     * Send a new message in an existing conversation.
     */
    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable String chatId,
            @RequestBody @Valid ChatMessageRequest request,
            Authentication authentication) {

        ChatMessageResponse message = chatService.sendMessage(authentication, chatId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent.", message));
    }

    /**
     * PUT /api/chats/{chatId}/read
     * Mark all messages in a conversation as read for the authenticated user.
     * The chatId is the partner ID (the other participant's ID).
     */
    @PutMapping("/{chatId}/read")
    public ResponseEntity<ApiResponse<Void>> markChatRead(
            @PathVariable String chatId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        chatService.markConversationRead(user.getId(), chatId);
        return ResponseEntity.ok(ApiResponse.success("Conversation marked as read."));
    }
}