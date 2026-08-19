package com.maitri.dto.vendor;

import com.maitri.model.VendorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vendor Response DTO — Phase 5.
 *
 * Public-facing shape of a vendor listing. Adds the resolved category
 * slug + name so the frontend can build URLs and badges without extra
 * lookups. Never contains email/password — those live on the users
 * collection and are not part of the business profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorResponse {

    private String id;
    private String userId;
    private String shopName;
    private String ownerName;
    private String categoryId;      // categories._id
    private String categorySlug;    // frontend URL slug (e.g. street-food)
    private String categoryName;    // display name (e.g. Street Food)
    private String description;
    private String address;
    private String area;
    private String phone;
    private String openingTime;
    private String closingTime;
    private List<String> images;
    private double averageRating;
    private VendorStatus status;
    private LocalDateTime createdAt;
}