package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.complaint.ComplaintResponse;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Complaint Vendor Controller — Phase 9 (Complaints Module) — VENDOR routes.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET /api/complaints/vendor/me  — VENDOR/ADMIN: complaints about own business
 *
 * NOTE: The path "/vendor/me" is deliberately declared BEFORE the user-facing
 * GET /api/complaints/{id} route (Spring MVC prefers the more specific literal
 * path over the {id} path variable — same mechanism as /api/reviews/my), so
 * there is no route conflict.
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                      | Anonymous | USER | VENDOR | ADMIN |
 *   |-------------------------------|-----------|------|--------|-------|
 *   | GET /api/complaints/vendor/me | 401       | 403  | ✅     | ✅    |
 *
 * The vendor identity is ALWAYS resolved from the authenticated JWT account
 * via Vendor.userId — never from a client-supplied vendorId. A vendor can
 * only ever see complaints about their own business (404 otherwise).
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintVendorController {

    private final ComplaintService complaintService;
    private final UserRepository userRepository;

    /** VENDOR/ADMIN only — lists complaints about the authenticated vendor's own business. */
    @GetMapping("/vendor/me")
    @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getVendorComplaints(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Complaints for your business retrieved.",
                        complaintService.getVendorComplaints(currentUser(userDetails)))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}