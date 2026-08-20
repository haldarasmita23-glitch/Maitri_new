package com.maitri.dto.favourite;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Favourite Request DTO — Phase 8.
 *
 * The request body for adding a vendor to the authenticated user's favourites.
 * Only the vendor ID is needed; the user is always derived from the JWT.
 *
 * Validation: vendorId is required and must not be blank (→ 400).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavouriteRequest {

    @NotBlank(message = "Vendor ID is required.")
    private String vendorId;
}