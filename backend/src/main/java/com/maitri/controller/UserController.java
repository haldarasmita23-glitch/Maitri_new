package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.auth.UserResponse;
import com.maitri.dto.user.UserUpdateRequest;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Controller — Phase 6.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET /api/users/me   — USER & ADMIN: current user's profile
 *   PUT /api/users/me   — USER & ADMIN: update current user's profile
 *
 * VENDOR accounts are deliberately DENIED (403): a vendor manages their
 * business listing via /api/vendors/*, not their (rarely used) user profile.
 * This matches the documented SECURITY.md access matrix.
 *
 * Role enforcement is split across two layers (defence in depth):
 *   - SecurityConfig  requires authentication at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   enforces the exact role at the method layer (403 for
 *                     the wrong role, e.g. a VENDOR calling /api/users/me)
 *
 * NOTE: /api/auth/me remains unchanged — it keeps serving the lightweight
 * authenticated identity for navbar state. /api/users/me is the richer,
 * editable profile endpoint.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    /** USER & ADMIN — returns the current user's editable profile. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("User profile retrieved.",
                        userService.getMe(currentUser(userDetails)))
        );
    }

    /** USER & ADMIN — updates the current user's editable profile. */
    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("User profile updated.",
                        userService.updateMe(currentUser(userDetails), request))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}
