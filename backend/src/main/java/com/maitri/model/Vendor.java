package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vendor — MongoDB Document representing a business LISTING PROFILE (Phase 5).
 *
 * ─── IDENTITY MODEL (Option A) ──────────────────────────────────────────────
 *   The `users` collection remains the SINGLE authentication/identity source.
 *   A vendor account is a User with role = VENDOR. This document is ONLY the
 *   business/listing profile for that account.
 *
 *   users._id
 *      └──> vendors.userId  (unique, 1:1)
 *
 *   There is NO second credential/password system. Login, JWT and BCrypt all
 *   continue to use the existing Phase 3A flow unchanged.
 *
 * ─── WORKFLOW ───────────────────────────────────────────────────────────────
 *   Apply      → status = PENDING   (submitted by an authenticated VENDOR)
 *   Admin      → approve            (status = APPROVED → publicly visible)
 *   Admin      → reject             (status = REJECTED → hidden)
 *
 * ─── FIELDS ─────────────────────────────────────────────────────────────────
 *   userId         — Reference to users._id (unique, one profile per account)
 *   shopName       — Display name of the business
 *   ownerName      — Owner / manager name
 *   categoryId     — Reference to categories._id (resolved from the slug)
 *   description    — Business description
 *   address, area  — Location details
 *   phone          — Business phone
 *   openingTime / closingTime — Opening hours (HH:mm)
 *   images         — Optional photo URLs
 *   averageRating  — 0 until the reviews module (Phase 7) ships
 *   status         — PENDING / APPROVED / REJECTED
 *   createdAt      — When the application was submitted
 *
 * INDEXES:
 *   userId     (unique) — enforces one profile per account
 *   categoryId (non-unique) — speeds up category filtering
 *
 * @Document(collection = "vendors") — maps to the MongoDB "vendors" collection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vendors")
public class Vendor {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String shopName;

    private String ownerName;

    @Indexed
    private String categoryId;

    private String description;

    private String address;

    private String area;

    private String phone;

    private String openingTime;

    private String closingTime;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Builder.Default
    private double averageRating = 0.0;

    @Builder.Default
    private VendorStatus status = VendorStatus.PENDING;

    private LocalDateTime createdAt;
}