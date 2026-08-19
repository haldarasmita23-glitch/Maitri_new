package com.maitri.service;

import com.maitri.dto.auth.UserResponse;
import com.maitri.dto.user.UserUpdateRequest;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * User Service — business logic for the User Module (Phase 6).
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   getMe()     — returns the current user's safe profile projection
 *   updateMe()  — updates the current user's editable profile fields
 *
 * ─── WHAT CANNOT BE EDITED ───────────────────────────────────────────────────
 *   email, role, active, password, createdAt are deliberately immutable here:
 *   - The request DTO has no fields for them, so clients cannot send them.
 *   - email/password changes need dedicated, verified flows (future phases).
 *   - role/active changes are ADMIN-only concerns (Phase 12).
 *
 * ─── MAPPING ─────────────────────────────────────────────────────────────────
 *   Mirrors AuthService.toUserResponse() so every user projection in the app
 *   is consistent and never leaks the password hash.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the current user's safe profile projection.
     *
     * @param user The resolved authenticated user (controller responsibility)
     * @return A password-free UserResponse
     */
    public UserResponse getMe(User user) {
        return toResponse(user);
    }

    /**
     * Updates the current user's editable profile fields.
     *
     * PUT semantics: all provided fields are applied; optional fields are
     * replaced wholesale. The language preference keeps its existing value
     * when the request omits it, so a client can never accidentally reset a
     * non-English preference to the default.
     *
     * @param user    The resolved authenticated user (controller responsibility)
     * @param request Validated editable fields
     * @return The updated password-free UserResponse
     */
    public UserResponse updateMe(User user, UserUpdateRequest request) {
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setPreferredLanguage(
                request.getPreferredLanguage() != null
                        ? request.getPreferredLanguage()
                        : user.getPreferredLanguage());
        user.setLocation(request.getLocation());
        user.setProfilePhoto(request.getProfilePhoto());
        user.setUpdatedAt(LocalDateTime.now());
        // email, role, active, password and createdAt are intentionally untouched

        User saved = userRepository.save(user);
        log.info("[User] Profile updated: id={}", saved.getId());
        return toResponse(saved);
    }

    /** Maps a User document to the safe API projection (never includes password). */
    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .phone(user.getPhone())
                .preferredLanguage(user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en")
                .location(user.getLocation())
                .profilePhoto(user.getProfilePhoto())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
