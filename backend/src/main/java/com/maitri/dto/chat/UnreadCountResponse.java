package com.maitri.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unread Count Response DTO — Phase 11.
 *
 * Returned by GET /api/chats/unread-count to drive the navbar badge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    /** Total number of unread messages for the authenticated account. */
    private long count;
}