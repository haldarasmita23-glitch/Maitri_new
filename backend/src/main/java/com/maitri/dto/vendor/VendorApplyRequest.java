package com.maitri.dto.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Vendor Application / Update Request DTO — Phase 5.
 *
 * Used by:
 *   POST /api/vendors/apply   — authenticated VENDOR submits a new listing
 *   PUT  /api/vendors/me      — VENDOR updates their own listing
 *
 * ─── categoryId NOTE ─────────────────────────────────────────────────────────
 *   The frontend works with category SLUGS (street-food, tailors, printing,
 *   repair). This field therefore carries the slug; VendorService resolves it
 *   to the real categories._id before storing it in the vendor document.
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   There is deliberately NO `status` or `averageRating` field here.
 *   Vendors can never set their own approval status or rating.
 *   Unknown JSON fields (e.g. a client sneaking in "status") are ignored by
 *   Jackson, so approval state can ONLY be changed by an ADMIN endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorApplyRequest {

    @NotBlank(message = "Shop name is required.")
    @Size(max = 100, message = "Shop name must be at most 100 characters.")
    private String shopName;

    @NotBlank(message = "Owner name is required.")
    @Size(max = 100, message = "Owner name must be at most 100 characters.")
    private String ownerName;

    @NotBlank(message = "Category is required.")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "Category must be a valid category slug (e.g. street-food)."
    )
    private String categoryId;

    @Size(max = 2000, message = "Description must be at most 2000 characters.")
    private String description;

    @NotBlank(message = "Address is required.")
    @Size(max = 300, message = "Address must be at most 300 characters.")
    private String address;

    @NotBlank(message = "Area is required.")
    @Size(max = 100, message = "Area must be at most 100 characters.")
    private String area;

    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Phone must be a valid 10-digit Indian mobile number.")
    private String phone;

    @NotBlank(message = "Opening time is required.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Opening time must be in HH:mm format.")
    private String openingTime;

    @NotBlank(message = "Closing time is required.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Closing time must be in HH:mm format.")
    private String closingTime;

    private List<String> images;

    /** Convenience helper for tests — always returns a mutable, non-null list. */
    public List<String> safeImages() {
        return images == null ? new ArrayList<>() : images;
    }
}