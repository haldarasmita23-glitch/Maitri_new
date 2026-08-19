package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.vendor.VendorApplyRequest;
import com.maitri.dto.vendor.VendorResponse;
import com.maitri.exception.InvalidCredentialsException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vendor Controller — Phase 5.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   POST   /api/vendors/apply            — VENDOR only: submit listing → 201 PENDING
 *   GET    /api/vendors                  — PUBLIC: approved vendors (slug + search)
 *   GET    /api/vendors/{id}             — PUBLIC: one approved vendor
 *   GET    /api/vendors/me               — VENDOR only: own listing
 *   PUT    /api/vendors/me               — VENDOR only: update own listing
 *   GET    /api/vendors/admin/pending    — ADMIN only: review queue
 *   PATCH  /api/vendors/{id}/approve     — ADMIN only
 *   PATCH  /api/vendors/{id}/reject      — ADMIN only
 *
 * Role enforcement is split across two layers (defence in depth):
 *   - SecurityConfig  allows/denies at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   enforces the exact role at the method layer (403 for
 *                     wrong role, e.g. a USER calling an ADMIN endpoint)
 */
@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final UserRepository userRepository;

    /** VENDOR only — submits a business listing (status = PENDING). */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> apply(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody VendorApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Vendor application submitted.",
                        vendorService.apply(currentUser(userDetails), request))
        );
    }

    /** PUBLIC — approved vendors, optionally filtered by category slug + search. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<VendorResponse>>> listApproved(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendors retrieved.", vendorService.listApproved(category, q))
        );
    }

    /** PUBLIC — a single approved vendor (PENDING/REJECTED return 404). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor retrieved.", vendorService.getApprovedById(id))
        );
    }

    /** VENDOR only — returns the authenticated vendor's own listing. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor profile retrieved.",
                        vendorService.getMyProfile(currentUser(userDetails)))
        );
    }

    /** VENDOR only — updates the authenticated vendor's listing (not status). */
    @PutMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody VendorApplyRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor profile updated.",
                        vendorService.updateMyProfile(currentUser(userDetails), request))
        );
    }

    /** ADMIN only — the pending review queue, oldest first. */
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<VendorResponse>>> listPending() {
        return ResponseEntity.ok(
                ApiResponse.success("Pending vendors retrieved.", vendorService.listPending())
        );
    }

    /** ADMIN only — approves a vendor (becomes publicly visible). */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorResponse>> approve(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor approved.", vendorService.approve(id))
        );
    }

    /** ADMIN only — rejects a vendor (stays hidden). */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorResponse>> reject(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor rejected.", vendorService.reject(id))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));
    }
}