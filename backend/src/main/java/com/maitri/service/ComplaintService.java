package com.maitri.service;

import com.maitri.dto.complaint.ComplaintCreateRequest;
import com.maitri.dto.complaint.ComplaintNoteRequest;
import com.maitri.dto.complaint.ComplaintResponse;
import com.maitri.dto.complaint.ComplaintStatusRequest;
import com.maitri.dto.complaint.ComplaintUpdateRequest;
import com.maitri.exception.ComplaintNotFoundException;
import com.maitri.exception.InvalidComplaintStatusException;
import com.maitri.exception.VendorNotFoundException;
import com.maitri.model.Complaint;
import com.maitri.model.ComplaintStatus;
import com.maitri.model.Notification;
import com.maitri.model.NotificationType;
import com.maitri.model.Role;
import com.maitri.repository.NotificationRepository;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.ComplaintRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complaint Service — business logic for the Complaints Module (Phase 9).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   1. Only authenticated USERs/ADMINs can raise complaints (enforced by controller)
 *   2. Complaints can only be raised against APPROVED vendors
 *   3. Multiple complaints by the same user against the same vendor are ALLOWED
 *      (no unique compound index — the documentation does not require it)
 *   4. USER can edit/delete a complaint only while it is PENDING
 *   5. VENDOR can update status of complaints about their own business:
 *        PENDING → IN_PROGRESS → RESOLVED  (cannot skip PENDING → RESOLVED)
 *   6. ADMIN can perform any status transition and add/update the adminNote
 *   7. RESOLVED complaints are locked for user edits
 *   8. All user/vendor operations are scoped to the authenticated identity —
 *      a user can never see another user's complaint, and a vendor can never
 *      see/update a complaint about another vendor's business
 *   9. The adminNote is INTERNAL — only populated in responses for ADMIN callers
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   createComplaint()      — USER/ADMIN: raise a complaint against an approved vendor
 *   getMyComplaints()      — USER/ADMIN: list the authenticated user's complaints
 *   getComplaint()         — USER/ADMIN: view one of the user's own complaints
 *   updateComplaint()       — USER/ADMIN: edit own complaint (PENDING only)
 *   deleteComplaint()      — USER/ADMIN: delete own complaint (PENDING only)
 *   getVendorComplaints()  — VENDOR/ADMIN: complaints about the vendor's own business
 *   updateStatus()         — VENDOR/ADMIN: advance complaint status
 *   adminGetAll()          — ADMIN: all complaints, optional status filter
 *   adminUpdateStatus()    — ADMIN: any status transition
 *   adminUpdateNote()      — ADMIN: set/update the internal adminNote
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ─── USER / ADMIN: create ────────────────────────────────────────────────

    /**
     * Raises a new complaint against an APPROVED vendor.
     *
     * @param user    The authenticated user (role=USER/ADMIN, enforced by controller)
     * @param request Complaint details (vendorId, complaintType, description)
     * @return The created complaint
     * @throws VendorNotFoundException if the vendor doesn't exist or isn't APPROVED
     */
    public ComplaintResponse createComplaint(User user, ComplaintCreateRequest request) {
        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Complaints can only be raised against approved vendors.");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint complaint = Complaint.builder()
                .userId(user.getId())
                .vendorId(vendor.getId())
                .complaintType(request.getComplaintType())
                .description(request.getDescription())
                .status(ComplaintStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Complaint saved = complaintRepository.save(complaint);
        log.info("[Complaint] Created: complaintId={}, userId={}, vendorId={}",
                saved.getId(), user.getId(), vendor.getId());
        return toResponse(saved, false);
    }

    // ─── USER / ADMIN: read ──────────────────────────────────────────────────

    /**
     * Lists the authenticated user's complaints, newest first.
     *
     * @param user The authenticated user
     * @return List of the user's complaints
     */
    public List<ComplaintResponse> getMyComplaints(User user) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(c -> toResponse(c, false))
                .collect(Collectors.toList());
    }

    /**
     * Returns one of the authenticated user's own complaints.
     * Scoped by userId — a user can never view another user's complaint (404).
     *
     * @param complaintId The complaint's ID
     * @param user        The authenticated user
     * @return The complaint
     * @throws ComplaintNotFoundException if the complaint doesn't exist or isn't the user's
     */
    public ComplaintResponse getComplaint(String complaintId, User user) {
        Complaint complaint = complaintRepository.findByIdAndUserId(complaintId, user.getId())
                .orElseThrow(() -> new ComplaintNotFoundException("Complaint not found or access denied."));
        return toResponse(complaint, false);
    }

    // ─── USER / ADMIN: update & delete ───────────────────────────────────────

    /**
     * Edits the authenticated user's own complaint. Only allowed while PENDING.
     *
     * @param complaintId The complaint's ID
     * @param user        The authenticated user
     * @param request     Updated complaint details
     * @return The updated complaint
     * @throws ComplaintNotFoundException if the complaint doesn't exist or isn't the user's
     * @throws InvalidComplaintStatusException if the complaint is no longer PENDING
     */
    public ComplaintResponse updateComplaint(String complaintId, User user, ComplaintUpdateRequest request) {
        Complaint complaint = complaintRepository.findByIdAndUserId(complaintId, user.getId())
                .orElseThrow(() -> new ComplaintNotFoundException("Complaint not found or access denied."));

        if (complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new InvalidComplaintStatusException(
                    "Complaints can only be edited while they are PENDING."
            );
        }

        complaint.setComplaintType(request.getComplaintType());
        complaint.setDescription(request.getDescription());
        complaint.setUpdatedAt(LocalDateTime.now());

        Complaint saved = complaintRepository.save(complaint);
        log.info("[Complaint] Updated: complaintId={}, userId={}", complaintId, user.getId());
        return toResponse(saved, false);
    }

    /**
     * Deletes the authenticated user's own complaint. Only allowed while PENDING.
     *
     * @param complaintId The complaint's ID
     * @param user        The authenticated user
     * @throws ComplaintNotFoundException if the complaint doesn't exist or isn't the user's
     * @throws InvalidComplaintStatusException if the complaint is no longer PENDING
     */
    public void deleteComplaint(String complaintId, User user) {
        Complaint complaint = complaintRepository.findByIdAndUserId(complaintId, user.getId())
                .orElseThrow(() -> new ComplaintNotFoundException("Complaint not found or access denied."));

        if (complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new InvalidComplaintStatusException(
                    "Complaints can only be deleted while they are PENDING."
            );
        }

        complaintRepository.deleteById(complaintId);
        log.info("[Complaint] Deleted: complaintId={}, userId={}", complaintId, user.getId());
    }

    // ─── VENDOR / ADMIN: vendor-facing ────────────────────────────────────────

    /**
     * Lists complaints about the authenticated vendor's own business, newest first.
     * The vendor identity is resolved from the authenticated account via
     * Vendor.userId — never from a client-supplied vendorId.
     *
     * @param user The authenticated vendor account
     * @return List of complaints about the vendor's business
     * @throws VendorNotFoundException if the account has no vendor profile
     */
    public List<ComplaintResponse> getVendorComplaints(User user) {
        Vendor vendor = findMyVendor(user);
        return complaintRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId())
                .stream()
                .map(c -> toResponse(c, false))
                .collect(Collectors.toList());
    }

    /**
     * Updates the status of a complaint. Role-aware:
     *   - ADMIN:  any transition (administrative override), no vendor scoping.
     *   - VENDOR: scoped to the vendor's own business, and enforces the workflow
     *             PENDING → IN_PROGRESS → RESOLVED (cannot skip PENDING → RESOLVED).
     *
     * The caller's role is never trusted from the request body — it comes from
     * the authenticated account (User.role).
     *
     * @param complaintId The complaint's ID
     * @param user        The authenticated user (VENDOR or ADMIN)
     * @param request     The requested new status
     * @return The updated complaint
     * @throws ComplaintNotFoundException if the complaint doesn't exist or isn't the vendor's business
     * @throws InvalidComplaintStatusException if the status is invalid or the transition is illegal
     */
    public ComplaintResponse updateStatus(String complaintId, User user, ComplaintStatusRequest request) {
        if (user.getRole() == Role.ADMIN) {
            return adminUpdateStatus(complaintId, request);
        }
        // VENDOR path — scoped to the vendor's own business
        Vendor vendor = findMyVendor(user);
        Complaint complaint = complaintRepository.findByIdAndVendorId(complaintId, vendor.getId())
                .orElseThrow(() -> new ComplaintNotFoundException("Complaint not found or access denied."));

        ComplaintStatus oldStatus = complaint.getStatus();
        ComplaintStatus target = parseStatus(request.getStatus());
        validateVendorTransition(oldStatus, target);

        complaint.setStatus(target);
        complaint.setUpdatedAt(LocalDateTime.now());

        Complaint saved = complaintRepository.save(complaint);
        log.info("[Complaint] Status updated by vendor: complaintId={}, vendorId={}, newStatus={}",
                complaintId, vendor.getId(), target);

        // Phase 10 trigger — COMPLAINT notification to the complainant on status change
        if (!oldStatus.equals(target)) {
            notificationService.notifyUser(
                    complaint.getUserId(),
                    Role.USER,
                    NotificationType.COMPLAINT,
                    "Complaint Status Updated",
                    "Your complaint status has been updated to " + target.name() + "."
            );
        }
        return toResponse(saved, false);
    }

    // ─── ADMIN: admin-facing ─────────────────────────────────────────────────

    /**
     * ADMIN: lists all complaints, newest first. Optional status filter.
     *
     * @param status Optional status filter (null = all)
     * @return List of all complaints
     */
    public List<ComplaintResponse> adminGetAll(ComplaintStatus status) {
        List<Complaint> complaints = (status == null)
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByStatusOrderByCreatedAtDesc(status);
        return complaints.stream()
                .map(c -> toResponse(c, true))
                .collect(Collectors.toList());
    }

    /**
     * ADMIN: updates a complaint's status. Admin may perform any transition.
     *
     * @param complaintId The complaint's ID
     * @param request     The requested new status
     * @return The updated complaint
     * @throws ComplaintNotFoundException if the complaint doesn't exist
     * @throws InvalidComplaintStatusException if the status value is invalid
     */
    public ComplaintResponse adminUpdateStatus(String complaintId, ComplaintStatusRequest request) {
        Complaint complaint = findById(complaintId);
        ComplaintStatus oldStatus = complaint.getStatus();
        ComplaintStatus target = parseStatus(request.getStatus());

        complaint.setStatus(target);
        complaint.setUpdatedAt(LocalDateTime.now());

        Complaint saved = complaintRepository.save(complaint);
        log.info("[Complaint] Status updated by admin: complaintId={}, newStatus={}",
                complaintId, target);

        // Phase 10 trigger — COMPLAINT notification to the complainant on status change
        if (!oldStatus.equals(target)) {
            notificationService.notifyUser(
                    complaint.getUserId(),
                    Role.USER,
                    NotificationType.COMPLAINT,
                    "Complaint Status Updated",
                    "Your complaint status has been updated to " + target.name() + "."
            );
        }
        return toResponse(saved, true);
    }

    /**
     * ADMIN: sets/updates the internal adminNote on a complaint.
     *
     * @param complaintId The complaint's ID
     * @param request     The admin note
     * @return The updated complaint (with adminNote populated)
     * @throws ComplaintNotFoundException if the complaint doesn't exist
     */
    public ComplaintResponse adminUpdateNote(String complaintId, ComplaintNoteRequest request) {
        Complaint complaint = findById(complaintId);

        complaint.setAdminNote(request.getAdminNote());
        complaint.setUpdatedAt(LocalDateTime.now());

        Complaint saved = complaintRepository.save(complaint);
        log.info("[Complaint] Admin note updated: complaintId={}", complaintId);
        return toResponse(saved, true);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Loads the authenticated account's vendor profile or throws 404. */
    private Vendor findMyVendor(User user) {
        return vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new VendorNotFoundException(
                        "No vendor profile found for this account."
                ));
    }

    /** Loads a complaint by id or throws 404. */
    private Complaint findById(String id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new ComplaintNotFoundException("Complaint not found."));
    }

    /** Parses a status string into the enum, throwing 400 for invalid values. */
    private ComplaintStatus parseStatus(String status) {
        try {
            return ComplaintStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidComplaintStatusException(
                    "Invalid complaint status. Must be one of: PENDING, IN_PROGRESS, RESOLVED."
            );
        }
    }

    /**
     * Validates a VENDOR status transition.
     * Allowed: PENDING → IN_PROGRESS, IN_PROGRESS → RESOLVED, and no-op (same status).
     * A vendor cannot skip PENDING → RESOLVED directly.
     */
    private void validateVendorTransition(ComplaintStatus current, ComplaintStatus target) {
        if (current == target) {
            return; // no-op is allowed
        }
        boolean legal = (current == ComplaintStatus.PENDING && target == ComplaintStatus.IN_PROGRESS)
                || (current == ComplaintStatus.IN_PROGRESS && target == ComplaintStatus.RESOLVED);
        if (!legal) {
            throw new InvalidComplaintStatusException(
                    "Vendors can only transition a complaint from PENDING to IN_PROGRESS, "
                            + "then from IN_PROGRESS to RESOLVED."
            );
        }
    }

    /**
     * Maps a Complaint entity to a ComplaintResponse DTO, enriching with the
     * complainant's name and the vendor's shop name.
     *
     * @param complaint       The complaint entity
     * @param includeAdminNote Whether to include the internal adminNote (ADMIN only)
     * @return Safe complaint data projection (never exposes credentials)
     */
    private ComplaintResponse toResponse(Complaint complaint, boolean includeAdminNote) {
        User complainant = userRepository.findById(complaint.getUserId()).orElse(null);
        Vendor vendor = vendorRepository.findById(complaint.getVendorId()).orElse(null);

        return ComplaintResponse.builder()
                .id(complaint.getId())
                .userId(complaint.getUserId())
                .userName(complainant != null ? complainant.getName() : "Unknown User")
                .vendorId(complaint.getVendorId())
                .vendorName(vendor != null ? vendor.getShopName() : "Unknown Vendor")
                .complaintType(complaint.getComplaintType())
                .description(complaint.getDescription())
                .status(complaint.getStatus())
                .adminNote(includeAdminNote ? complaint.getAdminNote() : null)
                .createdAt(complaint.getCreatedAt())
                .updatedAt(complaint.getUpdatedAt())
                .build();
    }
}