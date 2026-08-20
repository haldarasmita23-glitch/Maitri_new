package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.favourite.FavouriteCheckResponse;
import com.maitri.dto.favourite.FavouriteRequest;
import com.maitri.dto.favourite.FavouriteResponse;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.FavouriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Favourite Controller — Phase 8 (Favourites Module).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET    /api/favourites           — USER/ADMIN: list the user's favourites
 *   POST   /api/favourites           — USER/ADMIN: add an approved vendor
 *   DELETE /api/favourites/{vendorId} — USER/ADMIN: remove a favourited vendor
 *   GET    /api/favourites/{vendorId} — USER/ADMIN: check favourite status
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                    | Anonymous | USER | VENDOR | ADMIN |
 *   |-----------------------------|-----------|------|--------|-------|
 *   | GET /api/favourites         | 401       | ✅   | 403    | ✅    |
 *   | POST /api/favourites        | 401       | ✅   | 403    | ✅    |
 *   | DELETE /api/favourites/*    | 401       | ✅   | 403    | ✅    |
 *   | GET /api/favourites/{id}    | 401       | ✅   | 403    | ✅    |
 *
 * Role enforcement is split across two layers (defense in depth):
 *   - SecurityConfig  requires authentication at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   enforces exact roles at method layer (403 for wrong role)
 *
 * All operations are scoped to the authenticated user — a user can never
 * access or remove another user's favourites.
 */
@RestController
@RequestMapping("/api/favourites")
@RequiredArgsConstructor
public class FavouriteController {

    private final FavouriteService favouriteService;
    private final UserRepository userRepository;

    /** USER/ADMIN only — lists the authenticated user's favourites. */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<FavouriteResponse>>> getFavourites(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Your favourites retrieved.",
                        favouriteService.getFavourites(currentUser(userDetails)))
        );
    }

    /** USER/ADMIN only — adds an approved vendor to the authenticated user's favourites. */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<FavouriteResponse>> addFavourite(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FavouriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Vendor added to favourites.",
                        favouriteService.addFavourite(currentUser(userDetails), request))
        );
    }

    /** USER/ADMIN only — removes a vendor from the authenticated user's favourites. */
    @DeleteMapping("/{vendorId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeFavourite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String vendorId) {
        favouriteService.removeFavourite(currentUser(userDetails), vendorId);
        return ResponseEntity.ok(
                ApiResponse.success("Vendor removed from favourites.")
        );
    }

    /** USER/ADMIN only — checks whether the authenticated user favourited a vendor. */
    @GetMapping("/{vendorId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<FavouriteCheckResponse>> checkFavourite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String vendorId) {
        return ResponseEntity.ok(
                ApiResponse.success("Favourite status retrieved.",
                        favouriteService.isFavourite(currentUser(userDetails), vendorId))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}