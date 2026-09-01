package com.maitri.dto.chat;

import com.maitri.model.MessageType;
import com.maitri.model.Role;
import com.maitri.model.TranslationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Message Response DTO — Phase 11 & Translation.
 *
 * Safe projection of a Chat message for API responses.
 * Contains display data, original text, translated text, and language metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    /** The message's id. */
    private String id;

    /** The sender's account id. */
    private String senderId;

    /** Role of the sender (USER | VENDOR | ADMIN). */
    private Role senderRole;

    /** The receiver's account id. */
    private String receiverId;

    /** Role of the receiver (USER | VENDOR | ADMIN). */
    private Role receiverRole;

    /** Message content (projected for viewer). */
    private String message;

    /** Original raw text message as sent. */
    private String originalMessage;

    /** Translated message in receiver's language. */
    private String translatedMessage;

    /** Source language code ("en", "hi", "kn"). */
    private String sourceLanguage;

    /** Target language code ("en", "hi", "kn"). */
    private String targetLanguage;

    /** Status of translation. */
    private TranslationStatus translationStatus;

    /** Category of the message. */
    private MessageType messageType;

    /** Whether the message has been read. */
    private boolean read;

    /** When the message was sent. */
    private LocalDateTime timestamp;

    /** True if the authenticated user is the sender. */
    @com.fasterxml.jackson.annotation.JsonProperty("isOwnMessage")
    private boolean isOwnMessage;

    /** Conversation request status: PENDING, ACCEPTED, REJECTED. */
    private com.maitri.model.ConversationStatus status;
}