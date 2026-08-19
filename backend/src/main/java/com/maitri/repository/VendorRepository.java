package com.maitri.repository;

import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Vendor Repository — data access layer for the Vendor profile (Phase 5).
 *
 * Derives queries from method names:
 *   findByUserId(...)                      → the profile for one users._id
 *   existsByUserId(...)                    → prevents duplicate profiles (409)
 *   findByStatus(...)                      → approved / pending lists
 *   findByStatusAndCategoryId(...)         → approved list filtered by category
 *   findByStatusOrderByCreatedAtAsc(...)   → oldest pending first (admin review)
 */
@Repository
public interface VendorRepository extends MongoRepository<Vendor, String> {

    /** Returns the vendor profile linked to a users._id (unique, 1:1). */
    Optional<Vendor> findByUserId(String userId);

    /** True when the user already has a vendor profile. */
    boolean existsByUserId(String userId);

    /** All vendors in a given status (e.g. all APPROVED for public browse). */
    List<Vendor> findByStatus(VendorStatus status);

    /** Approved vendors in a specific category (categoryId = categories._id). */
    List<Vendor> findByStatusAndCategoryId(VendorStatus status, String categoryId);

    /** Pending vendors, oldest first — the admin review queue. */
    List<Vendor> findByStatusOrderByCreatedAtAsc(VendorStatus status);
}