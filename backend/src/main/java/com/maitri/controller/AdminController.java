package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.admin.UserManagementRequest;
import com.maitri.dto.vendor.VendorResponse;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.repository.UserRepository;
import com.maitri.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin Controller — Phase 12 (Admin Module).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET    /api/admin/users               — ADMIN only: list all users (optional role filter)
 *   GET    /api/admin/users/{email}        — ADMIN only: find user by email
 *   PUT    /api/admin/users               — ADMIN only: update user fields
 *   DELETE /api/admin/users/{email}        — ADMIN only: deactivate a user account
 *   GET    /api/admin/vendors/pending      — ADMIN only: list pending vendor applications
 *   PATCH  /api/admin/vendors/{id}/approve — ADMIN only: approve vendor
 *   PATCH  /api/admin/vendors/{id}/reject  — ADMIN only: reject vendor
 *
 * All responses use the standard ApiResponse<T> wrapper.
 * Errors throw domain-specific exceptions handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final VendorService vendorService;

    /**
     * GET /api/admin/users
     * List all users. Optional role filter via ?role=USER|VENDOR|ADMIN
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> listUsers(
            @RequestParam(required = false) Role role) {
        List<User> users = (role != null)
                ? userRepository.findByRole(role)
                : userRepository.findAll();

        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        String roleName = role != null ? role.name() : "all";
        return ResponseEntity.ok(ApiResponse.success(
                "Users retrieved (" + roleName + ").", userIds));
    }

    /**
     * GET /api/admin/users/{email}
     * Find a specific user by email address.
     * Returns 404 if no user with that email exists.
     */
    @GetMapping("/users/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> getUserByEmail(
            @PathVariable String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(
                        "No user found with email: " + email));
        return ResponseEntity.ok(ApiResponse.success(
                "User retrieved.", user.getId()));
    }

    /**
     * PUT /api/admin/users
     * Update a user's name, role, or active status.
     * Identified by email in the request body.
     * Returns 404 if the user does not exist.
     */
    @PutMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @Valid @RequestBody UserManagementRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Email is required to identify the user."));
        }

        User existing = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(
                        "No user found with email: " + request.getEmail()));

        if (request.getName() != null)   existing.setName(request.getName());
        if (request.getRole() != null)   existing.setRole(request.getRole());
        if (request.getActive() != null) existing.setActive(request.getActive());
        existing.setUpdatedAt(LocalDateTime.now());
        userRepository.save(existing);

        return ResponseEntity.ok(ApiResponse.success("User updated successfully."));
    }

    /**
     * DELETE /api/admin/users/{email}
     * Deactivate a user account (soft delete — sets active=false).
     * Returns 404 if the user does not exist.
     */
    @DeleteMapping("/users/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(
                        "No user found with email: " + email));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully."));
    }

    /**
     * GET /api/admin/vendors/pending
     * List all vendor applications awaiting approval.
     */
    @GetMapping("/vendors/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> listPendingVendors() {
        List<String> pendingIds = vendorService.listPending().stream()
                .map(VendorResponse::getId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(
                "Pending vendors retrieved (" + pendingIds.size() + ").", pendingIds));
    }

    /**
     * PATCH /api/admin/vendors/{id}/approve
     * Approve a vendor application. Returns 404 if vendor not found.
     */
    @PatchMapping("/vendors/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveVendor(@PathVariable String id) {
        vendorService.approve(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor approved successfully."));
    }

    /**
     * PATCH /api/admin/vendors/{id}/reject
     * Reject a vendor application. Returns 404 if vendor not found.
     */
    @PatchMapping("/vendors/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectVendor(@PathVariable String id) {
        vendorService.reject(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor rejected successfully."));
    }
}