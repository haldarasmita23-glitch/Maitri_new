package com.maitri.dto.chat;

import com.maitri.model.MessageType;
import com.maitri.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Conversation Response DTO — Phase 11.
 *
 * Represents a conversation summary for the chat list view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationResponse {

    /** The other party's id. */
    private String otherPartyId;

    /** The other party's name. */
    private String otherPartyName;

    /** The other party's role (USER | VENDOR | ADMIN). */
    private Role otherPartyRole;

    /** The last message content (truncated if long). */
    private String lastMessage;

    /** Type of the last message. */
    private MessageType lastMessageType;

    /** When the last message was sent. */
    private LocalDateTime lastMessageTimestamp;

    /** Whether the last message was sent by the current user. */
    private boolean lastMessageIsOwn;

    /** Number of unread messages from this party. */
    private long unreadCount;
}