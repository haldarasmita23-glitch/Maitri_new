package com.maitri.service;

import com.maitri.dto.notification.NotificationResponse;
import com.maitri.dto.notification.UnreadCountResponse;
import com.maitri.exception.NotificationNotFoundException;
import com.maitri.model.Notification;
import com.maitri.model.NotificationType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notification Service — business logic for the Notifications Module (Phase 10).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   1. Notifications are created ONLY by system triggers (vendor approval/
 *      rejection, complaint status changes, new reviews) — never by clients.
 *   2. {@link #notifyUser} is FAIL-SAFE: it catches every exception and only
 *      logs, so a notification problem can never break the primary operation
 *      (approval, complaint update, review submission).
 *   3. All read operations are scoped to the authenticated account's userId —
 *      an account can never see or modify another account's notifications
 *      (other-account ids map to 404, not 403).
 *   4. Marking a notification as read is IDEMPOTENT — marking an already-read
 *      notification succeeds without changing anything.
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   notifyUser()        — internal trigger entry point (fail-safe, never throws)
 *   getMyNotifications() — list the authenticated account's notifications
 *   getUnreadCount()     — count of unread notifications (navbar badge)
 *   markAsRead()         — mark one owned notification read (idempotent)
 *   markAllAsRead()      — mark every unread notification read
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final com.maitri.repository.UserRepository userRepository;

    // ─── Trigger entry point (used by other services) ────────────────────────

    /**
     * Broadcasts a notification to all active ADMIN accounts (fail-safe).
     *
     * @param type    The notification category
     * @param title   Short title
     * @param message Notification body
     */
    public void notifyAdmins(NotificationType type, String title, String message) {
        try {
            List<User> admins = userRepository.findByRole(Role.ADMIN);
            for (User admin : admins) {
                notifyUser(admin.getId(), Role.ADMIN, type, title, message);
            }
        } catch (Exception ex) {
            log.warn("[Notification] Failed to notify admins ({}): {}", type, ex.getMessage());
        }
    }

    /**
     * Creates a notification for a target account. FAIL-SAFE by design:
     * any exception is caught and logged so that the triggering business
     * operation (vendor approval/rejection, complaint status update, review
     * submission) can never fail because of a notification problem.
     *
     * @param userId   The target account's id (users._id)
     * @param userRole The target account's role
     * @param type     The notification category
     * @param title    Short title shown in the UI
     * @param message  Notification body text
     */
    public void notifyUser(String userId, Role userRole, NotificationType type,
                           String title, String message) {
        try {
            Notification notification = notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .userRole(userRole)
                    .type(type)
                    .title(title)
                    .message(message)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("[Notification] Created: id={}, userId={}, type={}",
                    notification.getId(), userId, type);
        } catch (Exception ex) {
            // Never let a notification failure break the primary operation.
            log.warn("[Notification] Failed to create notification for user {} ({}): {}",
                    userId, type, ex.getMessage());
        }
    }

    // ─── Authenticated account: read ─────────────────────────────────────────

    /**
     * Lists the authenticated account's notifications, newest first.
     *
     * @param user The authenticated account (any role)
     * @return The account's notifications
     */
    public List<NotificationResponse> getMyNotifications(User user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Counts the authenticated account's unread notifications.
     *
     * @param user The authenticated account (any role)
     * @return The unread count
     */
    public UnreadCountResponse getUnreadCount(User user) {
        return UnreadCountResponse.builder()
                .count(notificationRepository.countByUserIdAndReadFalse(user.getId()))
                .build();
    }

    // ─── Authenticated account: mark read ────────────────────────────────────

    /**
     * Marks one of the authenticated account's notifications as read.
     * Idempotent: marking an already-read notification succeeds unchanged.
     * Scoped by userId — unknown ids AND other accounts' ids both return 404.
     *
     * @param notificationId The notification's id
     * @param user           The authenticated account
     * @return The (updated) notification
     * @throws NotificationNotFoundException if the notification doesn't exist
     *                                       or belongs to another account
     */
    public NotificationResponse markAsRead(String notificationId, User user) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Notification not found."));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
            log.info("[Notification] Marked read: id={}, userId={}", notificationId, user.getId());
        }
        return toResponse(notification);
    }

    /**
     * Marks ALL of the authenticated account's unread notifications as read.
     * A no-op when there are no unread notifications.
     *
     * @param user The authenticated account
     */
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(user.getId());
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
        log.info("[Notification] Marked {} notification(s) read for user {}",
                unread.size(), user.getId());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Maps a Notification entity to a NotificationResponse DTO.
     *
     * @param notification The notification entity
     * @return Safe notification projection (never exposes credentials)
     */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .userRole(notification.getUserRole())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
