package com.maitri.dto.favourite;

import com.maitri.dto.vendor.VendorResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Favourite Response DTO — Phase 8.
 *
 * Public-facing shape of a single saved favourite. Embeds the vendor details
 * using the existing VendorResponse shape so the frontend can render the
 * saved business without extra lookups. Never contains credentials/passwords —
 * the embedded VendorResponse deliberately excludes email and password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavouriteResponse {

    private String id;
    private String userId;
    private String vendorId;
    private VendorResponse vendor;
    private LocalDateTime createdAt;
}