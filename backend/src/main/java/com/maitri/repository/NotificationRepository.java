package com.maitri.repository;

import com.maitri.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Notification Repository — data access for the Notifications Module (Phase 10).
 *
 * ─── ACCESS PATTERNS ─────────────────────────────────────────────────────────
 *   Every query is scoped by userId — an account can only ever reach its own
 *   notifications. There is deliberately NO unscoped findAll-style finder for
 *   application use, and no cross-user lookup.
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   findByUserIdOrderByCreatedAtDesc — list an account's notifications, newest first
 *   countByUserIdAndReadFalse        — unread badge count
 *   findByIdAndUserId                — fetch one notification owned by the account
 *                                      (unknown id OR another account's id → empty)
 *   findByUserIdAndReadFalse         — all unread notifications (for mark-all-read)
 */
public interface NotificationRepository extends MongoRepository<Notification, String> {

    /**
     * Lists all notifications belonging to an account, newest first.
     *
     * @param userId The account's id (users._id)
     * @return The account's notifications
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Counts the account's unread notifications (drives the navbar badge).
     *
     * @param userId The account's id (users._id)
     * @return Number of unread notifications
     */
    long countByUserIdAndReadFalse(String userId);

    /**
     * Fetches a single notification only if it belongs to the given account.
     * Returns empty for unknown ids AND for other accounts' ids — both map to 404,
     * so no information about other users' notifications can ever leak.
     *
     * @param id     The notification's id
     * @param userId The account's id (users._id)
     * @return The notification if present and owned by the account
     */
    Optional<Notification> findByIdAndUserId(String id, String userId);

    /**
     * Lists all of the account's unread notifications (used by mark-all-read).
     *
     * @param userId The account's id (users._id)
     * @return The account's unread notifications
     */
    List<Notification> findByUserIdAndReadFalse(String userId);
}
