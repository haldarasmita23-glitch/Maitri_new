package com.maitri.dto.notification;

import com.maitri.model.NotificationType;
import com.maitri.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification Response DTO — Phase 10.
 *
 * Safe projection of a Notification for API responses. Contains only
 * display data — never credentials or any other account secrets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /** The notification's id. */
    private String id;

    /** The owning account's id (users._id). */
    private String userId;

    /** Role of the owning account (USER | VENDOR | ADMIN). */
    private Role userRole;

    /** Short notification title. */
    private String title;

    /** Notification body text. */
    private String message;

    /** Category of the notification. */
    private NotificationType type;

    /** Whether the notification has been read. */
    private boolean read;

    /** When the notification was created. */
    private LocalDateTime createdAt;
}
