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
 * Complaint — MongoDB Document representing a user's complaint against a vendor (Phase 9).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   - Only authenticated USERs (and ADMINs) can raise complaints
 *   - Complaints can only be raised against APPROVED vendors
 *   - Multiple complaints by the same user against the same vendor are ALLOWED
 *     (no unique compound index — the documentation does not require it)
 *   - USER can edit/delete a complaint only while it is PENDING
 *   - VENDOR can update status of complaints about their own business:
 *       PENDING → IN_PROGRESS → RESOLVED  (cannot skip PENDING → RESOLVED)
 *   - ADMIN can perform any status transition and add/update the adminNote
 *   - RESOLVED complaints are locked for user edits
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "complaints"
 *
 * ─── FIELDS ──────────────────────────────────────────────────────────────────
 *   id            — MongoDB ObjectId (auto-generated, String representation)
 *   userId        — Reference to users._id (the complainant)
 *   vendorId      — Reference to vendors._id (the business complained about)
 *   complaintType — Category of complaint (e.g. "Service", "Quality", "Billing")
 *   description   — Free-text description of the complaint
 *   status        — PENDING | IN_PROGRESS | RESOLVED
 *   adminNote     — Optional internal admin comment (never exposed to public)
 *   createdAt     — When the complaint was raised
 *   updatedAt     — When the complaint was last modified (status/edits)
 *
 * ─── INDEXES ─────────────────────────────────────────────────────────────────
 *   { userId }    — Fast lookup of a user's complaints (non-unique)
 *   { vendorId }  — Fast lookup of a vendor's complaints (non-unique)
 *   { status }    — Fast admin filtering by status (non-unique)
 *
 * ─── ACCESS PATTERNS ─────────────────────────────────────────────────────────
 *   - Find all complaints for a user (authenticated user only)
 *   - Find a complaint by id + userId (ownership verification)
 *   - Find all complaints for a vendor (vendor's own business only)
 *   - Find a complaint by id + vendorId (vendor ownership verification)
 *   - Find all complaints (admin), optionally filtered by status
 *
 * @Document("complaints") — Maps this class to the MongoDB "complaints" collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "complaints")
public class Complaint {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     */
    @Id
    private String id;

    /**
     * Reference to the user who raised this complaint.
     * Points to users._id (the complainant).
     */
    @Indexed
    private String userId;

    /**
     * Reference to the vendor being complained about.
     * Points to vendors._id (the business).
     */
    @Indexed
    private String vendorId;

    /**
     * Category of the complaint (e.g. "Service", "Quality", "Billing").
     * Required, non-blank.
     */
    private String complaintType;

    /**
     * Free-text description of the complaint.
     * Required, non-blank, max 1000 characters.
     */
    private String description;

    /**
     * Lifecycle state of the complaint.
     * Default: PENDING (newly raised).
     */
    @Builder.Default
    private ComplaintStatus status = ComplaintStatus.PENDING;

    /**
     * Optional internal admin comment.
     * Only visible to ADMINs — never exposed in public complaint responses.
     */
    private String adminNote;

    /**
     * Timestamp of when this complaint was first raised.
     * Set once during creation. Never changed after that.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this complaint.
     * Updated whenever the complaint is edited or its status changes.
     */
    private LocalDateTime updatedAt;
}