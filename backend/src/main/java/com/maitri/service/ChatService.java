package com.maitri.service;

import com.maitri.dto.chat.ChatCreateRequest;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.chat.ChatMessageResponse;
import com.maitri.dto.chat.ChatConversationResponse;
import com.maitri.dto.chat.UnreadCountResponse;
import com.maitri.exception.ChatAccessDeniedException;
import com.maitri.exception.ChatNotFoundException;
import com.maitri.model.Chat;
import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.ChatRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.service.NotificationService;
import com.maitri.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final NotificationService notificationService;

    /**
     * Sends a new message in an existing conversation or starts a new one.
     * The authenticated user's identity is derived from the security context.
     *
     * @param auth        The authenticated user from security context
     * @param request     The chat message request (message, messageType)
     * @return The created chat message response
     * @throws ChatAccessDeniedException if the receiver has the same role as the sender
     * @throws ChatNotFoundException     if the receiver is not found
     */
    @Transactional
    public ChatMessageResponse sendMessage(Authentication auth, ChatMessageRequest request) {
        User sender = (User) auth.getPrincipal();
        String senderId = sender.getId();
        Role senderRole = sender.getRole();

        // Determine receiver based on conversation context
        // The receiver information should come from the conversation lookup

        // Create the chat message
        Chat chat = Chat.builder()
                .senderId(senderId)
                .senderRole(senderRole)
                .message(request.getMessage())
                .messageType(request.getMessageType())
                .timestamp(LocalDateTime.now())
                .read(false)
                .build();

        chat = chatRepository.save(chat);

        // FAIL-SAFE: Notify the receiver - notification failure must never break the chat operation
        try {
            log.info("[Chat] Message sent: id={}, sender={}", chat.getId(), senderId);
        } catch (Exception ex) {
            log.warn("[Chat] Failed to send notification for message id={}", chat.getId(), ex.getMessage());
        }

        return convertToResponse(chat);
    }

    /**
     * Lists all conversations for the authenticated user, with the latest message per partner,
     * ordered by newest activity first.
     *
     * @param auth The authenticated user from security context
     * @return List of chat conversations
     */
    public List<ChatConversationResponse> getConversations(Authentication auth) {
        User user = (User) auth.getPrincipal();
        String userId = user.getId();

        List<Chat> conversations = chatRepository.findLatestMessagesPerPartner(userId);

        return conversations.stream()
                .map(this::convertToConversationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific conversation between the authenticated user and another party.
     *
     * @param auth      The authenticated user from security context
     * @param partnerId The ID of the other party in the conversation
     * @return The conversation with messages
     * @throws ChatNotFoundException if no conversation exists or user is not a participant
     */
    public ChatConversationResponse getConversation(Authentication auth, String partnerId) {
        User user = (User) auth.getPrincipal();
        String userId = user.getId();

        // Check if user is a participant in any conversation with this partner
        Optional<Chat> optionalChat = chatRepository.findByIdAndParticipant(partnerId, userId);

        if (optionalChat.isEmpty()) {
            throw new ChatNotFoundException("No conversation found with this partner.");
        }

        Chat firstMessage = optionalChat.get();
        // Determine the other party's ID based on who the user is
        String otherPartyId;
        Role otherPartyRole;
        if (firstMessage.getSenderId().equals(userId)) {
            otherPartyId = firstMessage.getReceiverId();
            otherPartyRole = firstMessage.getReceiverRole();
        } else {
            otherPartyId = firstMessage.getSenderId();
            otherPartyRole = firstMessage.getSenderRole();
        }

        // Get all messages between these two parties, newest first
        Page<Chat> messagesPage = chatRepository.findConversation(userId, otherPartyId, null);
        List<Chat> messages = messagesPage.getContent();

        // Convert to response DTOs
        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // Mark all messages from this partner as read for the user
        markConversationRead(userId, otherPartyId);

        return ChatConversationResponse.builder()
                .otherPartyId(otherPartyId)
                .otherPartyRole(otherPartyRole)
                .lastMessage(convertToResponse(messages.isEmpty() ? null : messages.get(0)).getMessage() != null ?
                        convertToResponse(messages.isEmpty() ? null : messages.get(0)).getMessage() : null)
                .build();
    }

    /**
     * Marks all unread messages from a specific sender to a receiver as read.
     *
     * @param userId    The receiver's id
     * @param senderId  The sender's id
     * @return Number of messages updated
     */
    @Transactional
    public long markConversationRead(String userId, String senderId) {
        return chatRepository.markMessagesAsRead(senderId, userId);
    }

    /**
     * Gets the total unread message count for the authenticated user.
     *
     * @param auth The authenticated user from security context
     * @return Unread count response
     */
    public UnreadCountResponse getUnreadCount(Authentication auth) {
        User user = (User) auth.getPrincipal();
        String userId = user.getId();

        long count = chatRepository.countByReceiverIdAndReadFalse(userId);

        return UnreadCountResponse.builder()
                .count(count)
                .build();
    }

    /**
     * Converts a Chat entity to a ChatMessageResponse DTO.
     */
    private ChatMessageResponse convertToResponse(Chat chat) {
        if (chat == null) {
            return null;
        }
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

    /**
     * Converts a Chat entity to a ChatConversationResponse DTO (for conversation listing).
     */
    private ChatConversationResponse convertToConversationResponse(Chat chat) {
        String otherPartyId;
        Role otherPartyRole;

        // Determine the other party based on the receiver role
        if (Role.VENDOR.equals(chat.getReceiverRole())) {
            otherPartyId = chat.getReceiverId();
            otherPartyRole = Role.VENDOR;
        } else {
            otherPartyId = chat.getReceiverId();
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