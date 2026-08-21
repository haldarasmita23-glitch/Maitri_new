package com.maitri.repository;

import com.maitri.model.Complaint;
import com.maitri.model.ComplaintStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Complaint Repository — MongoDB data access layer for complaints (Phase 9).
 *
 * ─── QUERY PATTERNS ──────────────────────────────────────────────────────────
 *   - Find all complaints for a specific user (newest first)
 *   - Find a complaint by id + userId (ownership verification)
 *   - Find all complaints for a specific vendor (newest first)
 *   - Find a complaint by id + vendorId (vendor ownership verification)
 *   - Find all complaints (admin), optionally filtered by status
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   Every user/vendor query is scoped by userId/vendorId so a user can only
 *   ever see their OWN complaints and a vendor only their OWN business's
 *   complaints. Admin queries are unscoped (admin has full visibility).
 *
 * ─── DUPLICATE POLICY ─────────────────────────────────────────────────────────
 *   Multiple complaints by the same user against the same vendor are ALLOWED.
 *   There is NO unique compound index on {userId, vendorId} — the documentation
 *   does not require one-complaint-per-user-per-vendor.
 */
@Repository
public interface ComplaintRepository extends MongoRepository<Complaint, String> {

    /**
     * Find all complaints raised by a specific user, newest first.
     * Used to render the authenticated user's complaint history.
     *
     * @param userId The user's ID
     * @return List of the user's complaints
     */
    List<Complaint> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find a specific complaint by id, but only if it belongs to the specified user.
     * Used for ownership verification when a user reads/updates/deletes a complaint.
     *
     * @param id     The complaint's ID
     * @param userId The user's ID (for ownership verification)
     * @return Optional containing the complaint if found and owned by user, empty otherwise
     */
    Optional<Complaint> findByIdAndUserId(String id, String userId);

    /**
     * Find all complaints about a specific vendor, newest first.
     * Used to render the vendor's complaint inbox (their own business only).
     *
     * @param vendorId The vendor's ID
     * @return List of complaints about the vendor
     */
    List<Complaint> findByVendorIdOrderByCreatedAtDesc(String vendorId);

    /**
     * Find a specific complaint by id, but only if it belongs to the specified vendor.
     * Used for ownership verification when a vendor updates a complaint's status.
     *
     * @param id       The complaint's ID
     * @param vendorId The vendor's ID (for ownership verification)
     * @return Optional containing the complaint if found and owned by vendor, empty otherwise
     */
    Optional<Complaint> findByIdAndVendorId(String id, String vendorId);

    /**
     * Find all complaints, newest first (admin view).
     *
     * @return List of all complaints
     */
    List<Complaint> findAllByOrderByCreatedAtDesc();

    /**
     * Find all complaints with a specific status, newest first (admin filter).
     *
     * @param status The complaint status to filter by
     * @return List of complaints with the given status
     */
    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);
}