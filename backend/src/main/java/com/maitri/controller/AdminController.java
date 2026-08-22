package com.maitri.controller;

import java.util.Optional;

import com.maitri.dto.admin.AdminResponse;
import com.maitri.dto.admin.UserManagementRequest;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final VendorService vendorService;

    /** GET /api/admin/users â€” List all users (ADMIN only).
     *  Optional role filter: ?role=USER|VENDOR|ADMIN|SUPER_ADMIN */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> listUsers(
            @RequestParam(required = false) Role role) {
        List<User> users;
        if (role != null) {
            users = userRepository.findByRole(role);
        } else {
            users = userRepository.findAll();
        }

        String roleName = role != null ? role.name() : "all";
        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("Users retrieved (" + roleName + ").");
        return ResponseEntity.ok(response);
    }

    /** GET /api/admin/users/{email} â€” Get user by email (ADMIN only) */
    @GetMapping("/users/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> getUserByEmail(
            @PathVariable String email) {
        Optional<User> user = userRepository.findByEmail(email);

        AdminResponse response = new AdminResponse();
        if (user.isPresent()) {
            response.setSuccess(true);
            response.setMessage("User retrieved: " + user.get().getEmail());
        } else {
            response.setSuccess(false);
            response.setMessage("User not found with email: " + email);
        }
        return ResponseEntity.ok(response);
    }

    /** PUT /api/admin/users â€” Update user (ADMIN only) */
    @PutMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> updateUser(
            @Valid @RequestBody UserManagementRequest request) {
        if (request.getEmail() == null) {
            AdminResponse errorResponse = new AdminResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Email is required to identify the user.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        User existing = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getRole() != null) {
            existing.setRole(request.getRole());
        }
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        existing.setUpdatedAt(LocalDateTime.now());
        userRepository.save(existing);

        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("User updated successfully.");
        return ResponseEntity.ok(response);
    }

    /** DELETE /api/admin/users/{email} â€” Deactivate user (ADMIN only) */
    @DeleteMapping("/users/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> deactivateUser(
            @PathVariable String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("User deactivated successfully.");
        return ResponseEntity.ok(response);
    }

    /** GET /api/admin/vendors/pending â€” List pending vendors (ADMIN only) */
    @GetMapping("/vendors/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> listPendingVendors() {
        List<String> pendingIds = vendorService.listPending().stream()
                .map(v -> v.getId())
                .collect(Collectors.toList());

        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("Pending vendors retrieved (" + pendingIds.size() + " vendors).");
        return ResponseEntity.ok(response);
    }

    /** PATCH /api/admin/vendors/{id}/approve â€” Approve vendor (ADMIN only) */
    @PatchMapping("/vendors/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> approveVendor(
            @PathVariable String id) {
        vendorService.approve(id);

        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("Vendor approved successfully.");
        return ResponseEntity.ok(response);
    }

    /** PATCH /api/admin/vendors/{id}/reject â€” Reject vendor (ADMIN only) */
    @PatchMapping("/vendors/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminResponse> rejectVendor(
            @PathVariable String id) {
        vendorService.reject(id);

        AdminResponse response = new AdminResponse();
        response.setSuccess(true);
        response.setMessage("Vendor rejected successfully.");
        return ResponseEntity.ok(response);
    }
}