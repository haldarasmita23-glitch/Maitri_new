package com.maitri.service;

import com.maitri.dto.chat.ChatCreateRequest;
import com.maitri.dto.chat.ChatMessageRequest;
import com.maitri.dto.chat.ChatMessageResponse;
import com.maitri.dto.chat.ChatConversationResponse;
import com.maitri.dto.chat.TranslationResult;
import com.maitri.dto.chat.UnreadCountResponse;
import com.maitri.exception.ChatAccessDeniedException;
import com.maitri.exception.ChatNotFoundException;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.Chat;
import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.TranslationStatus;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.repository.ChatRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ChatService — Business logic for the Chat / Contact Module (Phase 11 & Multilingual Translation).
 *
 * ─── PRINCIPAL RESOLUTION ────────────────────────────────────────────────────
 *   We do NOT cast auth.getPrincipal() to User directly because
 *   UserDetailsServiceImpl returns a plain Spring Security UserDetails, not our
 *   custom User entity. Instead we use auth.getName() (the email address) and
 *   look up the User entity from UserRepository when the full entity is needed.
 *
 * ─── TRANSLATION FLOW ────────────────────────────────────────────────────────
 *   On sendMessage:
 *     1. Target recipient's preferredLanguage is resolved from User document.
 *     2. Source language is detected from text script or sender's preferredLanguage.
 *     3. TranslationService translates to receiver's language.
 *     4. originalMessage, translatedMessage, sourceLanguage, targetLanguage,
 *        and translationStatus are stored in the Chat document.
 */
@Service
@RequiredArgsConstructor
public class ChatService {
    private final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final NotificationService notificationService;
    private final TranslationService translationService;

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

    /**
     * Resolves receiver's preferred language code by partnerId (User ID or Vendor ID).
     */
    private String resolveReceiverLanguage(String partnerId) {
        if (partnerId == null || partnerId.isBlank()) {
            return "en";
        }

        Optional<User> directUser = userRepository.findById(partnerId);
        if (directUser.isPresent() && directUser.get().getPreferredLanguage() != null && !directUser.get().getPreferredLanguage().isBlank()) {
            return directUser.get().getPreferredLanguage().trim().toLowerCase();
        }

        // If partnerId is a Vendor document ID, find linked User
        Optional<Vendor> vendor = vendorRepository.findById(partnerId);
        if (vendor.isPresent()) {
            return userRepository.findById(vendor.get().getUserId())
                    .map(User::getPreferredLanguage)
                    .filter(lang -> lang != null && !lang.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .orElse("en");
        }

        return "en";
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

        // Check if a conversation between sender and receiver already exists
        Page<Chat> existing = chatRepository.findConversation(
                sender.getId(), receiverId, PageRequest.of(0, 1));
        if (!existing.isEmpty()) {
            Chat latest = existing.getContent().get(0);
            return convertToConversationResponse(latest, sender.getId());
        }

        String receiverLang = resolveReceiverLanguage(receiverId);
        String senderLang   = sender.getPreferredLanguage() != null ? sender.getPreferredLanguage() : "en";
        TranslationResult translation = translationService.translate("Conversation started", receiverLang, senderLang);

        // Create the opening "conversation started" message
        Chat chat = Chat.builder()
                .senderId(sender.getId())
                .senderRole(senderRole)
                .receiverId(receiverId)
                .receiverRole(receiverRole)
                .message("Conversation started")
                .originalMessage(translation.getOriginalText())
                .translatedMessage(translation.getTranslatedText())
                .sourceLanguage(translation.getSourceLanguage())
                .targetLanguage(translation.getTargetLanguage())
                .translationStatus(translation.getStatus())
                .status(com.maitri.model.ConversationStatus.PENDING)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .read(false)
                .build();

        chat = chatRepository.save(chat);
        log.info("[Chat] Conversation started: id={}, sender={}, receiver={}, status={}",
                chat.getId(), sender.getId(), receiverId, chat.getStatus());

        // Notify receiver about new conversation request
        try {
            String targetUserId = receiverId;
            Optional<Vendor> v = vendorRepository.findById(receiverId);
            if (v.isPresent()) {
                targetUserId = v.get().getUserId();
            }
            notificationService.notifyUser(
                    targetUserId,
                    receiverRole,
                    NotificationType.CHAT,
                    "New Customer Inquiry",
                    "Customer " + sender.getName() + " has sent a conversation request."
            );
        } catch (Exception ex) {
            log.warn("[Chat] Failed to send start conversation notification: {}", ex.getMessage());
        }

        return convertToConversationResponse(chat, sender.getId());
    }

    /**
     * Sends a new message in an existing conversation identified by partnerId.
     * Automatically translates message to receiver's preferred language.
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

        String receiverLang = resolveReceiverLanguage(partnerId);
        String senderLang   = sender.getPreferredLanguage() != null ? sender.getPreferredLanguage() : "en";

        TranslationResult translation = translationService.translate(
                request.getMessage(), receiverLang, senderLang);

        // Check existing conversation status
        com.maitri.model.ConversationStatus currentStatus = com.maitri.model.ConversationStatus.PENDING;
        Page<Chat> existing = chatRepository.findConversation(sender.getId(), partnerId, PageRequest.of(0, 1));
        if (!existing.isEmpty()) {
            Chat latest = existing.getContent().get(0);
            if (latest.getStatus() != null) {
                currentStatus = latest.getStatus();
            }
        }

        // If vendor or admin sends a message, conversation becomes ACCEPTED
        if (senderRole == Role.VENDOR || senderRole == Role.ADMIN) {
            currentStatus = com.maitri.model.ConversationStatus.ACCEPTED;
            if (!existing.isEmpty()) {
                Page<Chat> allMsgs = chatRepository.findConversation(sender.getId(), partnerId, PageRequest.of(0, 50));
                for (Chat c : allMsgs.getContent()) {
                    if (c.getStatus() != com.maitri.model.ConversationStatus.ACCEPTED) {
                        c.setStatus(com.maitri.model.ConversationStatus.ACCEPTED);
                        chatRepository.save(c);
                    }
                }
            }
        }

        Chat chat = Chat.builder()
                .senderId(sender.getId())
                .senderRole(senderRole)
                .receiverId(partnerId)
                .receiverRole(receiverRole)
                .message(request.getMessage())
                .originalMessage(translation.getOriginalText())
                .translatedMessage(translation.getTranslatedText())
                .sourceLanguage(translation.getSourceLanguage())
                .targetLanguage(translation.getTargetLanguage())
                .translationStatus(translation.getStatus())
                .status(currentStatus)
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .read(false)
                .build();

        chat = chatRepository.save(chat);

        // Notify receiver with translated message — failure must never break the chat operation
        try {
            String targetUserId = partnerId;
            Optional<Vendor> v = vendorRepository.findById(partnerId);
            if (v.isPresent()) {
                targetUserId = v.get().getUserId();
            }

            String notifyBody = translation.getTranslatedText() != null
                    ? translation.getTranslatedText()
                    : request.getMessage();
            notificationService.notifyUser(
                    targetUserId,
                    receiverRole,
                    NotificationType.CHAT,
                    "New message",
                    "New message from " + sender.getName() + ": " + notifyBody
            );
        } catch (Exception ex) {
            log.warn("[Chat] Failed to send notification for message id={}: {}",
                    chat.getId(), ex.getMessage());
        }

        log.info("[Chat] Message sent: id={}, sender={}, receiver={}, srcLang={}, tgtLang={}, status={}",
                chat.getId(), sender.getId(), partnerId,
                translation.getSourceLanguage(), translation.getTargetLanguage(), translation.getStatus());

        return convertToResponse(chat, sender.getId());
    }

    /**
     * Accepts a pending conversation request from a customer.
     *
     * @param auth      The authenticated vendor/admin caller
     * @param partnerId The customer's account ID
     * @return The updated conversation response
     */
    @Transactional
    public ChatConversationResponse acceptConversation(Authentication auth, String partnerId) {
        User user = resolveUser(auth);
        String userId = user.getId();

        Page<Chat> page = chatRepository.findConversation(userId, partnerId, PageRequest.of(0, 50));
        if (page.isEmpty()) {
            throw new ChatNotFoundException("No conversation found with partner: " + partnerId);
        }

        List<Chat> messages = page.getContent();
        for (Chat c : messages) {
            c.setStatus(com.maitri.model.ConversationStatus.ACCEPTED);
        }
        chatRepository.saveAll(messages);

        try {
            notificationService.notifyUser(
                    partnerId,
                    Role.USER,
                    NotificationType.CHAT,
                    "Conversation Request Accepted",
                    "Your conversation request has been accepted by " + user.getName()
            );
        } catch (Exception ex) {
            log.warn("[Chat] Failed to send acceptance notification: {}", ex.getMessage());
        }

        log.info("[Chat] Conversation accepted between vendor={} and customer={}", userId, partnerId);
        return convertToConversationResponse(messages.get(0), userId);
    }

    /**
     * Lists all conversations for the authenticated user (latest message per partner).
     *
     * @param auth The authenticated user from security context
     * @return List of chat conversations
     */
    public List<ChatConversationResponse> getConversations(Authentication auth) {
        User user = resolveUser(auth);
        String userId = user.getId();
        List<Chat> allMessages = chatRepository.findLatestMessagesPerPartner(userId);

        Map<String, Chat> latestPerPartner = new LinkedHashMap<>();
        for (Chat chat : allMessages) {
            String partnerId = chat.getSenderId().equals(userId) ? chat.getReceiverId() : chat.getSenderId();
            latestPerPartner.putIfAbsent(partnerId, chat);
        }

        return latestPerPartner.values().stream()
                .map(chat -> convertToConversationResponse(chat, userId))
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

        // Find the most recent message between this user and the partner (newest first).
        Page<Chat> page = chatRepository.findConversation(
                userId, partnerId, PageRequest.of(0, 1));
        if (page.isEmpty()) {
            throw new ChatNotFoundException("No conversation found with partner: " + partnerId);
        }

        Chat latestMessage = page.getContent().get(0);
        String otherPartyId;

        if (latestMessage.getSenderId().equals(userId)) {
            otherPartyId = latestMessage.getReceiverId();
        } else {
            otherPartyId = latestMessage.getSenderId();
        }

        // Mark messages from the partner as read
        markConversationRead(userId, otherPartyId);

        return convertToConversationResponse(latestMessage, userId);
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

    /**
     * Returns a paginated list of all messages in the conversation between the
     * authenticated user and the given partner, projected from the caller's perspective.
     *
     * <p>The underlying repository query returns messages newest-first. The frontend
     * should reverse the list before rendering so that older messages appear at the top.
     *
     * @param auth      The authenticated caller
     * @param partnerId The conversation partner's ID (same as chatId in URL)
     * @param pageable  Page + size — default page=0, size=30
     * @return Page of ChatMessageResponse, newest first
     */
    public Page<ChatMessageResponse> getMessages(
            Authentication auth, String partnerId, Pageable pageable) {

        User user   = resolveUser(auth);
        String userId = user.getId();

        Page<Chat> page = chatRepository.findConversation(userId, partnerId, pageable);

        // Mark unread messages from the partner as read while we're here
        markConversationRead(userId, partnerId);

        return page.map(chat -> convertToResponse(chat, userId));
    }

    // ─── Private converters ───────────────────────────────────────────────────

    /** Converts a Chat entity to a ChatMessageResponse DTO projected for the viewer. */
    private ChatMessageResponse convertToResponse(Chat chat, String currentUserId) {
        if (chat == null) return null;
        boolean isOwn = currentUserId != null && currentUserId.equals(chat.getSenderId());

        // For sender: primary display is original message. For receiver: primary display is translated message.
        String displayMsg;
        if (isOwn) {
            displayMsg = chat.getOriginalMessage() != null ? chat.getOriginalMessage() : chat.getMessage();
        } else {
            displayMsg = chat.getTranslatedMessage() != null
                    ? chat.getTranslatedMessage()
                    : (chat.getOriginalMessage() != null ? chat.getOriginalMessage() : chat.getMessage());
        }

        com.maitri.model.ConversationStatus status = chat.getStatus() != null
                ? chat.getStatus()
                : com.maitri.model.ConversationStatus.ACCEPTED;

        return ChatMessageResponse.builder()
                .id(chat.getId())
                .senderId(chat.getSenderId())
                .senderRole(chat.getSenderRole())
                .receiverId(chat.getReceiverId())
                .receiverRole(chat.getReceiverRole())
                .message(displayMsg)
                .originalMessage(chat.getOriginalMessage() != null ? chat.getOriginalMessage() : chat.getMessage())
                .translatedMessage(chat.getTranslatedMessage())
                .sourceLanguage(chat.getSourceLanguage())
                .targetLanguage(chat.getTargetLanguage())
                .translationStatus(chat.getTranslationStatus())
                .messageType(chat.getMessageType())
                .timestamp(chat.getTimestamp())
                .read(chat.isRead())
                .isOwnMessage(isOwn)
                .status(status)
                .build();
    }

    /** Converts a Chat entity to a ChatConversationResponse DTO relative to the current user. */
    private ChatConversationResponse convertToConversationResponse(Chat chat, String currentUserId) {
        String otherPartyId;
        Role   otherPartyRole;
        boolean isOwn = chat.getSenderId().equals(currentUserId);

        if (isOwn) {
            otherPartyId   = chat.getReceiverId();
            otherPartyRole = chat.getReceiverRole();
        } else {
            otherPartyId   = chat.getSenderId();
            otherPartyRole = chat.getSenderRole();
        }

        // Resolve other party display name
        String otherPartyName = null;
        if (otherPartyRole == Role.VENDOR) {
            otherPartyName = vendorRepository.findByUserId(otherPartyId)
                    .map(Vendor::getShopName)
                    .orElseGet(() -> userRepository.findById(otherPartyId)
                            .map(User::getName)
                            .orElse(otherPartyId));
        } else {
            otherPartyName = userRepository.findById(otherPartyId)
                    .map(User::getName)
                    .orElse(otherPartyId);
        }

        long unreadCount = isOwn ? 0 : chatRepository.findBySenderIdAndReceiverIdAndReadFalse(otherPartyId, currentUserId).size();

        // Viewer perspective for last message preview
        String lastDisplayMsg;
        if (isOwn) {
            lastDisplayMsg = chat.getOriginalMessage() != null
                    ? chat.getOriginalMessage()
                    : (chat.getMessage() != null ? chat.getMessage() : "");
        } else {
            lastDisplayMsg = chat.getTranslatedMessage() != null
                    ? chat.getTranslatedMessage()
                    : (chat.getOriginalMessage() != null ? chat.getOriginalMessage() : (chat.getMessage() != null ? chat.getMessage() : ""));
        }

        com.maitri.model.ConversationStatus status = chat.getStatus() != null
                ? chat.getStatus()
                : com.maitri.model.ConversationStatus.ACCEPTED;

        return ChatConversationResponse.builder()
                .otherPartyId(otherPartyId)
                .otherPartyName(otherPartyName)
                .otherPartyRole(otherPartyRole)
                .lastMessage(lastDisplayMsg)
                .originalMessage(chat.getOriginalMessage() != null ? chat.getOriginalMessage() : chat.getMessage())
                .translatedMessage(chat.getTranslatedMessage())
                .sourceLanguage(chat.getSourceLanguage())
                .targetLanguage(chat.getTargetLanguage())
                .translationStatus(chat.getTranslationStatus())
                .lastMessageType(chat.getMessageType())
                .lastMessageTimestamp(chat.getTimestamp())
                .lastMessageIsOwn(isOwn)
                .unreadCount(unreadCount)
                .status(status)
                .build();
    }
}