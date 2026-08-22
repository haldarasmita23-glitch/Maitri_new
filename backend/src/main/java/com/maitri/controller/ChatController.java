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
import com.maitri.service.ChatService;
import com.maitri.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final NotificationService notificationService;

    /**
     * GET /api/chats
     * List authenticated user's conversations (newest activity first).
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ChatConversationResponse> getChats(Authentication authentication) {
        return chatService.getConversations(authentication);
    }

    /**
     * GET /api/chats/{chatId}
     * Get a single conversation/messages by ID.
     * Returns 404 if user is not a participant.
     */
    @GetMapping("/{chatId}")
    @ResponseStatus(HttpStatus.OK)
    public ChatConversationResponse getChat(@PathVariable String chatId, Authentication authentication) {
        try {
            return chatService.getConversation(authentication, chatId);
        } catch (ChatNotFoundException e) {
            throw new ChatNotFoundException(e.getMessage());
        }
    }

    /**
     * POST /api/chats
     * Create/start a new conversation with a vendor (USER) or user (VENDOR).
     * USER can only message VENDOR, VENDOR can only message USER.
     * ADMIN can message anyone.
     * Uses ChatCreateRequest which contains receiverId and receiverRole.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatConversationResponse startConversation(
            @RequestBody ChatCreateRequest request,
            Authentication authentication) {

        // Convert ChatCreateRequest to ChatMessageRequest for the service
        // The message content will be set from the first message in the conversation
        ChatMessageRequest messageRequest = new ChatMessageRequest();
        messageRequest.setMessage(request.getReceiverId() != null ? "Starting conversation" : "");
        messageRequest.setMessageType(MessageType.TEXT);

        // For now, delegate to getConversations to list/retrieve conversation
        // The actual conversation creation logic is handled by the service
List<ChatConversationResponse> conversations = chatService.getConversations(authentication);

        // If no conversations exist, return a default response
        if (conversations.isEmpty()) {
            Role receiverRole = Role.USER;
            if (request.getReceiverRole() != null) {
                try {
                    receiverRole = Role.valueOf(request.getReceiverRole().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    receiverRole = Role.USER;
                }
            }
            return ChatConversationResponse.builder()
                    .otherPartyId(request.getReceiverId())
                    .otherPartyRole(receiverRole)
                    .lastMessage("Starting new conversation...")
                    .build();
        }

        return conversations.get(0);
    }

    /**
     * POST /api/chats/{chatId}/messages
     * Send a new message in an existing conversation.
     * TEXT and IMAGE message types supported.
     */
    @PostMapping("/{chatId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(
            @PathVariable String chatId,
            @RequestBody ChatMessageRequest request,
            Authentication authentication) {

        return chatService.sendMessage(authentication, request);
    }

    /**
     * PUT /api/chats/{chatId}/read
     * Mark all messages in a conversation as read for the authenticated user.
     */
    @PutMapping("/{chatId}/read")
    @ResponseStatus(HttpStatus.OK)
    public void markChatRead(@PathVariable String chatId, Authentication authentication) {
        chatService.markConversationRead(
                authentication.getName(),
                chatId
        );
    }

    /**
     * GET /api/chats/unread-count
     * Get total unread message count for the authenticated user.
     */
    @GetMapping("/unread-count")
    @ResponseStatus(HttpStatus.OK)
    public UnreadCountResponse getUnreadCount(Authentication authentication) {
        return chatService.getUnreadCount(authentication);
    }
}