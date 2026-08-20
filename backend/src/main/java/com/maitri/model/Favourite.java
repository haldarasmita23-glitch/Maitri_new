package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Favourite — MongoDB Document representing a user's saved vendor (Phase 8).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   - Only authenticated USERs (and ADMINs) can add favourites
 *   - Only APPROVED vendors can be favourited
 *   - One favourite per user per vendor (enforced by unique compound index)
 *   - Favourites are always scoped to the authenticated user — a user can
 *     never access or remove another user's favourites
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "favourites"
 *
 * ─── FIELDS ──────────────────────────────────────────────────────────────────
 *   id         — MongoDB ObjectId (auto-generated, String representation)
 *   userId     — Reference to users._id (the user who saved the vendor)
 *   vendorId   — Reference to vendors._id (the saved business)
 *   createdAt  — When the favourite was added
 *
 * ─── INDEXES ─────────────────────────────────────────────────────────────────
 *   { userId, vendorId } — Unique compound index (no duplicate favourites)
 *   { userId }           — Fast lookup of a user's favourite list
 *   { vendorId }         — Fast lookup of which users favourited a vendor
 *
 * ─── ACCESS PATTERNS ─────────────────────────────────────────────────────────
 *   - Find all favourites for a user (authenticated user only)
 *   - Check whether a user already favourited a vendor (before adding)
 *   - Remove a favourite (scoped by userId + vendorId)
 *
 * @Document("favourites") — Maps this class to the MongoDB "favourites" collection.
 * @CompoundIndex — Creates the unique compound index at the database level.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "favourites")
@CompoundIndex(name = "user_vendor_unique", def = "{'userId': 1, 'vendorId': 1}", unique = true)
public class Favourite {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     */
    @Id
    private String id;

    /**
     * Reference to the user who saved this vendor.
     * Points to users._id (the favouriting user).
     */
    @Indexed
    private String userId;

    /**
     * Reference to the favourited vendor.
     * Points to vendors._id (the saved business).
     */
    @Indexed
    private String vendorId;

    /**
     * Timestamp of when this favourite was created.
     * Set once when the user saves the vendor. Never changed after that.
     */
    private LocalDateTime createdAt;
}