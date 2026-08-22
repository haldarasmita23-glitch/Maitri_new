package com.maitri.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat Create Request DTO — Phase 11.
 *
 * Used to start a new conversation or retrieve existing one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCreateRequest {

    /** The id of the other party (vendor for USER, user for VENDOR). */
    private String receiverId;

    /** The role of the receiver (USER or VENDOR). */
    private String receiverRole;
}