package com.maitri.dto.favourite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Favourite Check Response DTO — Phase 8.
 *
 * Returned by GET /api/favourites/{vendorId} to tell the frontend whether
 * the authenticated user has already favourited a vendor. Drives the state
 * of favourite buttons without fetching the entire favourites list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavouriteCheckResponse {

    private boolean favourited;
}