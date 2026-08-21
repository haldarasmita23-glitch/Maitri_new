package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Notification — MongoDB Document representing an in-app notification (Phase 10).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   - Every notification belongs to exactly one account (userId) and is only
 *     ever readable/mutable by that account
 *   - Notifications are created by the system (triggers), never by clients —
 *     there is no "create notification" API endpoint
 *   - A notification starts unread (read = false) and can be marked read,
 *     either individually or all at once; marking is idempotent
 *   - Notification creation must NEVER break the primary operation that
 *     triggered it (approval, complaint update, review submission)
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "notifications"
 *
 * ─── FIELDS (per DATABASE_DESIGN.md §7) ──────────────────────────────────────
 *   id         — MongoDB ObjectId (auto-generated, String representation)
 *   userId     — Reference to users._id (the target account: user/vendor/admin)
 *   userRole   — Role of the target account (USER | VENDOR | ADMIN)
 *   title      — Short notification title
 *   message    — Notification body
 *   type       — GENERAL | COMPLAINT | REVIEW | VERIFICATION
 *   read       — false = unread
 *   createdAt  — When the notification was created
 *
 * ─── INDEXES ─────────────────────────────────────────────────────────────────
 *   { userId } — Fast lookup of an account's notification list.
 *                No unique constraint (multiple notifications per account).
 *
 * @Document("notifications") — Maps this class to the MongoDB collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
public class Notification {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     */
    @Id
    private String id;

    /**
     * Reference to the target account (users._id).
     * All reads/writes are scoped to this id — never trust a client-supplied userId.
     */
    @Indexed
    private String userId;

    /**
     * Role of the target account at creation time (USER | VENDOR | ADMIN).
     * Denormalised for display/filtering convenience.
     */
    private Role userRole;

    /** Short notification title shown in the UI. */
    private String title;

    /** Notification body text. */
    private String message;

    /** Category of the notification. */
    private NotificationType type;

    /**
     * Read flag. false = unread. Marking as read is idempotent.
     */
    @Builder.Default
    private boolean read = false;

    /**
     * Timestamp of when this notification was created.
     * Set once at creation. Never changed after that.
     */
    private LocalDateTime createdAt;
}
