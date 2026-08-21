package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.complaint.ComplaintCreateRequest;
import com.maitri.dto.complaint.ComplaintResponse;
import com.maitri.dto.complaint.ComplaintStatusRequest;
import com.maitri.dto.complaint.ComplaintUpdateRequest;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Complaint Controller — Phase 9 (Complaints Module) — USER/ADMIN routes.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   POST   /api/complaints           — USER/ADMIN: raise a complaint
 *   GET    /api/complaints/my        — USER/ADMIN: list own complaints
 *   GET    /api/complaints/{id}      — USER/ADMIN: view own complaint
 *   PUT    /api/complaints/{id}      — USER/ADMIN: edit own complaint (PENDING only)
 *   DELETE /api/complaints/{id}      — USER/ADMIN: delete own complaint (PENDING only)
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                 | Anonymous | USER | VENDOR | ADMIN |
 *   |--------------------------|-----------|------|--------|-------|
 *   | POST /api/complaints     | 401       | ✅   | 403    | ✅    |
 *   | GET /api/complaints/my   | 401       | ✅   | 403    | ✅    |
 *   | GET /api/complaints/{id} | 401       | ✅   | 403    | ✅    |
 *   | PUT /api/complaints/{id} | 401       | ✅   | 403    | ✅    |
 *   | DELETE /api/complaints/{id} | 401    | ✅   | 403    | ✅    |
 *
 * Role enforcement is split across two layers (defense in depth):
 *   - SecurityConfig  requires authentication at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   enforces exact roles at method layer (403 for wrong role)
 *
 * All operations are scoped to the authenticated user — a user can never
 * access or modify another user's complaints.
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final UserRepository userRepository;

    /** USER/ADMIN only — raises a complaint against an approved vendor. */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaint(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ComplaintCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Complaint submitted successfully.",
                        complaintService.createComplaint(currentUser(userDetails), request))
        );
    }

    /** USER/ADMIN only — lists the authenticated user's complaints. */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getMyComplaints(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Your complaints retrieved.",
                        complaintService.getMyComplaints(currentUser(userDetails)))
        );
    }

    /** USER/ADMIN only — views one of the authenticated user's own complaints. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> getComplaint(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Complaint retrieved.",
                        complaintService.getComplaint(id, currentUser(userDetails)))
        );
    }

    /** USER/ADMIN only — edits the authenticated user's own complaint (PENDING only). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateComplaint(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody ComplaintUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Complaint updated successfully.",
                        complaintService.updateComplaint(id, currentUser(userDetails), request))
        );
    }

    /** USER/ADMIN only — deletes the authenticated user's own complaint (PENDING only). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteComplaint(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        complaintService.deleteComplaint(id, currentUser(userDetails));
        return ResponseEntity.ok(
                ApiResponse.success("Complaint deleted successfully.")
        );
    }

    /** VENDOR/ADMIN only — updates a complaint's status (shared status endpoint).
     *  The service branches by the authenticated role:
     *    - VENDOR: scoped to the vendor's own business, PENDING→IN_PROGRESS→RESOLVED only
     *    - ADMIN:  any transition (administrative override) */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateComplaintStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody ComplaintStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Complaint status updated.",
                        complaintService.updateStatus(id, currentUser(userDetails), request))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}