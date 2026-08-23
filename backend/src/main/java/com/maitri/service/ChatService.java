package com.maitri.service;

import com.maitri.dto.chat.ChatCreateRequest;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.chat.ChatMessageResponse;
import com.maitri.dto.chat.ChatConversationResponse;
import com.maitri.dto.chat.UnreadCountResponse;
import com.maitri.exception.ChatAccessDeniedException;
import com.maitri.exception.ChatNotFoundException;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.Chat;
import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.ChatRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ChatService — Business logic for the Chat / Contact Module (Phase 11).
 *
 * ─── PRINCIPAL RESOLUTION ────────────────────────────────────────────────────
 *   We do NOT cast auth.getPrincipal() to User directly because
 *   UserDetailsServiceImpl returns a plain Spring Security UserDetails, not our
 *   custom User entity. Instead we use auth.getName() (the email address) and
 *   look up the User entity from UserRepository when the full entity is needed.
 *
 * ─── MESSAGING RULES ─────────────────────────────────────────────────────────
 *   Role enforcement is performed in ChatController before calling this service.
 *   The service trusts that role-compatible pairs have already been validated.
 */
@Service
@RequiredArgsConstructor
public class ChatService {
    private final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Resolves the caller's User entity from the authentication context.
     * Uses auth.getName() (email) — works regardless of principal type.
     */
    private User resolveUser(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found: " + email));
    }

    /**
     * Extracts the caller's Role from their granted authorities.
     */
    private Role resolveRole(Authentication auth) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ROLE_ADMIN".equals(a))  return Role.ADMIN;
            if ("ROLE_VENDOR".equals(a)) return Role.VENDOR;
        }
        return Role.USER;
    }

    // ─── Public service methods ───────────────────────────────────────────────

    /**
     * Starts a new conversation (or retrieves an existing one) between the
     * authenticated user and the specified receiver.
     *
     * @param auth       The authenticated caller
     * @param receiverId The ID of the receiver
     * @param receiverRole The role of the receiver
     * @return A ChatConversationResponse for the conversation
     */
    @Transactional
    public ChatConversationResponse startConversation(
            Authentication auth, String receiverId, Role receiverRole) {

        User sender    = resolveUser(auth);
        Role senderRole = resolveRole(auth);

        // Create the opening "conversation started" message
        Chat chat = Chat.builder()
                .senderId(sender.getId())
                .senderRole(senderRole)
                .receiverId(receiverId)
                .receiverRole(receiverRole)
                .message("Conversation started")
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .read(false)
                .build();

        chat = chatRepository.save(chat);
        log.info("[Chat] Conversation started: id={}, sender={}, receiver={}",
                chat.getId(), sender.getId(), receiverId);

        return ChatConversationResponse.builder()
                .otherPartyId(receiverId)
                .otherPartyRole(receiverRole)
                .lastMessage(chat.getMessage())
                .lastMessageType(chat.getMessageType())
                .lastMessageTimestamp(chat.getTimestamp())
                .build();
    }

    /**
     * Sends a new message in an existing conversation identified by partnerId.
     *
     * @param auth      The authenticated caller
     * @param partnerId The conversation partner's ID (acts as chatId in URL)
     * @param request   The message content and type
     * @return The created message response
     */
    @Transactional
    public ChatMessageResponse sendMessage(
            Authentication auth, String partnerId, ChatMessageRequest request) {

        User sender     = resolveUser(auth);
        Role senderRole = resolveRole(auth);

        // Determine receiver role (opposite of sender's role; ADMIN defaults to USER receiver)
        Role receiverRole = (senderRole == Role.USER) ? Role.VENDOR
                          : (senderRole == Role.VENDOR) ? Role.USER
                          : Role.USER;

        Chat chat = Chat.builder()
                .senderId(sender.getId())
                .senderRole(senderRole)
                .receiverId(partnerId)
                .receiverRole(receiverRole)
                .message(request.getMessage())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .read(false)
                .build();

        chat = chatRepository.save(chat);

        // Notify receiver — failure must never break the chat operation
        try {
            notificationService.notifyUser(
                    partnerId,
                    receiverRole,
                    NotificationType.CHAT,
                    "New message",
                    "New message from " + sender.getName()
            );
        } catch (Exception ex) {
            log.warn("[Chat] Failed to send notification for message id={}: {}",
                    chat.getId(), ex.getMessage());
        }

        log.info("[Chat] Message sent: id={}, sender={}, receiver={}",
                chat.getId(), sender.getId(), partnerId);

        return convertToResponse(chat);
    }

    /**
     * Lists all conversations for the authenticated user (latest message per partner).
     *
     * @param auth The authenticated user from security context
     * @return List of chat conversations
     */
    public List<ChatConversationResponse> getConversations(Authentication auth) {
        User user = resolveUser(auth);
        List<Chat> conversations = chatRepository.findLatestMessagesPerPartner(user.getId());
        return conversations.stream()
                .map(this::convertToConversationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific conversation using the partner's ID as the lookup key.
     *
     * @param auth      The authenticated user from security context
     * @param partnerId The conversation partner's ID (the chatId in the URL)
     * @return The conversation response
     * @throws ChatNotFoundException if no conversation found or user not a participant
     */
    public ChatConversationResponse getConversation(Authentication auth, String partnerId) {
        User user   = resolveUser(auth);
        String userId = user.getId();

        // Find the most recent message between this user and the partner.
        // Throws 404 if no conversation exists or the user is not a participant.
        Optional<Chat> optionalChat = chatRepository.findFirstConversationBetween(userId, partnerId);
        if (optionalChat.isEmpty()) {
            throw new ChatNotFoundException("No conversation found with partner: " + partnerId);
        }

        Chat firstMessage = optionalChat.get();
        String otherPartyId;
        Role otherPartyRole;

        if (firstMessage.getSenderId().equals(userId)) {
            otherPartyId   = firstMessage.getReceiverId();
            otherPartyRole = firstMessage.getReceiverRole();
        } else {
            otherPartyId   = firstMessage.getSenderId();
            otherPartyRole = firstMessage.getSenderRole();
        }

        // Get all messages between these two parties
        Page<Chat> messagesPage = chatRepository.findConversation(
                userId, otherPartyId, PageRequest.of(0, 50));
        List<Chat> messages = messagesPage.getContent();

        // Mark messages from the partner as read
        markConversationRead(userId, otherPartyId);

        String lastMsg = messages.isEmpty() ? null
                : convertToResponse(messages.get(0)).getMessage();

        return ChatConversationResponse.builder()
                .otherPartyId(otherPartyId)
                .otherPartyRole(otherPartyRole)
                .lastMessage(lastMsg)
                .build();
    }

    /**
     * Marks all unread messages from senderId to receiverId as read.
     *
     * @param receiverId The user marking messages as read
     * @param senderId   The sender whose messages will be marked read
     * @return Number of messages updated
     */
    @Transactional
    public long markConversationRead(String receiverId, String senderId) {
        List<Chat> unread = chatRepository.findBySenderIdAndReceiverIdAndReadFalse(senderId, receiverId);
        if (unread.isEmpty()) {
            return 0L;
        }
        unread.forEach(c -> c.setRead(true));
        chatRepository.saveAll(unread);
        return unread.size();
    }

    /**
     * Gets the total unread message count for the authenticated user.
     *
     * @param auth The authenticated user from security context
     * @return Unread count response
     */
    public UnreadCountResponse getUnreadCount(Authentication auth) {
        User user  = resolveUser(auth);
        long count = chatRepository.countByReceiverIdAndReadFalse(user.getId());
        return UnreadCountResponse.builder().count(count).build();
    }

    // ─── Private converters ───────────────────────────────────────────────────

    /** Converts a Chat entity to a ChatMessageResponse DTO. */
    private ChatMessageResponse convertToResponse(Chat chat) {
        if (chat == null) return null;
        return ChatMessageResponse.builder()
                .id(chat.getId())
                .senderId(chat.getSenderId())
                .senderRole(chat.getSenderRole())
                .receiverId(chat.getReceiverId())
                .receiverRole(chat.getReceiverRole())
                .message(chat.getMessage())
                .messageType(chat.getMessageType())
                .timestamp(chat.getTimestamp())
                .read(chat.isRead())
                .build();
    }

    /** Converts a Chat entity to a ChatConversationResponse DTO. */
    private ChatConversationResponse convertToConversationResponse(Chat chat) {
        String otherPartyId;
        Role   otherPartyRole;

        if (Role.VENDOR.equals(chat.getReceiverRole())) {
            otherPartyId   = chat.getReceiverId();
            otherPartyRole = Role.VENDOR;
        } else {
            otherPartyId   = chat.getReceiverId();
            otherPartyRole = Role.USER;
        }

        ChatMessageResponse lastMessage = convertToResponse(chat);

        return ChatConversationResponse.builder()
                .otherPartyId(otherPartyId)
                .otherPartyRole(otherPartyRole)
                .lastMessage(lastMessage != null ? lastMessage.getMessage() : "")
                .lastMessageType(lastMessage != null ? lastMessage.getMessageType() : null)
                .lastMessageTimestamp(lastMessage != null ? lastMessage.getTimestamp() : null)
                .build();
    }
}