package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.notification.NotificationResponse;
import com.maitri.dto.notification.UnreadCountResponse;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Notification Controller — Phase 10 (Notifications Module).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET /api/notifications              — any authenticated account: own list
 *   GET /api/notifications/unread-count — any authenticated account: unread badge count
 *   PUT /api/notifications/{id}/read    — any authenticated account: mark one read
 *   PUT /api/notifications/read-all     — any authenticated account: mark all read
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                        | Anonymous | USER | VENDOR | ADMIN |
 *   |---------------------------------|-----------|------|--------|-------|
 *   | GET    /api/notifications       | 401       | ✅   | ✅     | ✅    |
 *   | GET    .../unread-count         | 401       | ✅   | ✅     | ✅    |
 *   | PUT    .../{id}/read            | 401       | ✅*  | ✅*    | ✅*   |
 *   | PUT    .../read-all             | 401       | ✅   | ✅     | ✅    |
 *
 *   (* only the account's OWN notifications — anything else is 404)
 *
 * All three roles participate because notifications target users, vendors,
 * and admins alike (DATABASE_DESIGN.md §7). Role enforcement is split across
 * two layers (defense in depth):
 *   - SecurityConfig  requires authentication at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   re-affirms authentication at the method layer
 *
 * There is deliberately NO create/delete endpoint — notifications are created
 * by system triggers only, and V1 keeps them until a later phase decides
 * otherwise. All operations are scoped to the authenticated account.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /** Any authenticated account — lists its own notifications, newest first. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Your notifications retrieved.",
                        notificationService.getMyNotifications(currentUser(userDetails)))
        );
    }

    /** Any authenticated account — count of its unread notifications. */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Unread notification count retrieved.",
                        notificationService.getUnreadCount(currentUser(userDetails)))
        );
    }

    /**
     * Any authenticated account — marks one of its OWN notifications as read.
     * Idempotent. Unknown or other-account ids return 404.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Notification marked as read.",
                        notificationService.markAsRead(id, currentUser(userDetails)))
        );
    }

    /** Any authenticated account — marks all of its unread notifications read. */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllAsRead(currentUser(userDetails));
        return ResponseEntity.ok(
                ApiResponse.success("All notifications marked as read.")
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}
