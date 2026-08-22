package com.maitri.dto.chat;

import com.maitri.model.MessageType;
import com.maitri.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Message Request DTO — Phase 11.
 *
 * Used to send a new message in an existing conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    /** The message content (text or image URL). */
    private String message;

    /** Type of message (TEXT or IMAGE). */
    private MessageType messageType;
}