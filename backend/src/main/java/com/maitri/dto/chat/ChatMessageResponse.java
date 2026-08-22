package com.maitri.dto.chat;

import com.maitri.model.MessageType;
import com.maitri.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Message Response DTO — Phase 11.
 *
 * Safe projection of a Chat message for API responses.
 * Contains only display data — never credentials or any other account secrets.
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

    /** Message content. */
    private String message;

    /** Category of the message. */
    private MessageType messageType;

    /** Whether the message has been read. */
    private boolean read;

    /** When the message was sent. */
    private LocalDateTime timestamp;

    /** True if the authenticated user is the sender. */
    private boolean isOwnMessage;
}