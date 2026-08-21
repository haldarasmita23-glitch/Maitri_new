package com.maitri.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unread Count Response DTO — Phase 10.
 *
 * Returned by GET /api/notifications/unread-count to drive the navbar
 * notification badge without fetching the entire notification list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    /** Number of unread notifications for the authenticated account. */
    private long count;
}
