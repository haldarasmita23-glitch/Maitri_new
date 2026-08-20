package com.maitri.repository;

import com.maitri.model.Favourite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Favourite Repository — MongoDB data access layer for favourites (Phase 8).
 *
 * ─── QUERY PATTERNS ──────────────────────────────────────────────────────────
 *   - Find all favourites for a specific user (newest first)
 *   - Check whether a user has favourited a specific vendor (uniqueness)
 *   - Find a specific favourite by user and vendor (for removal)
 *   - Count a user's favourites (for display)
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   Every query is scoped by userId so a user can only ever see or remove
 *   their OWN favourites. The unique compound index {userId, vendorId}
 *   prevents duplicate entries at the database level.
 */
@Repository
public interface FavouriteRepository extends MongoRepository<Favourite, String> {

    /**
     * Find all favourites for a specific user, newest first.
     * Used to render the authenticated user's favourites list.
     *
     * @param userId The user's ID
     * @return List of the user's favourites
     */
    List<Favourite> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Check whether a user has already favourited a specific vendor.
     * Used to enforce the "one favourite per user per vendor" rule.
     *
     * @param userId The user's ID
     * @param vendorId The vendor's ID
     * @return true if the user already favourited this vendor
     */
    boolean existsByUserIdAndVendorId(String userId, String vendorId);

    /**
     * Find a specific favourite by user and vendor.
     * Used to verify a favourite exists before removing it.
     *
     * @param userId The user's ID
     * @param vendorId The vendor's ID
     * @return Optional containing the favourite if found, empty otherwise
     */
    Optional<Favourite> findByUserIdAndVendorId(String userId, String vendorId);

    /**
     * Delete a specific favourite, scoped by user and vendor.
     * Only removes the authenticated user's own favourite.
     *
     * @param userId The user's ID
     * @param vendorId The vendor's ID
     */
    void deleteByUserIdAndVendorId(String userId, String vendorId);

    /**
     * Count how many vendors a user has favourited.
     *
     * @param userId The user's ID
     * @return Number of favourites for the user
     */
    long countByUserId(String userId);
}