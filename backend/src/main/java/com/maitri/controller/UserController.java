package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.auth.UserResponse;
import com.maitri.dto.user.LanguagePreferenceRequest;
import com.maitri.dto.user.UserPreferenceResponse;
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
 * User Controller — Profile & User Preferences.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET /api/users/me                    — USER & ADMIN: current user's profile
 *   PUT /api/users/me                    — USER & ADMIN: update current user's profile
 *   GET /api/users/preferences           — USER, ADMIN & VENDOR: user's preferences
 *   PUT /api/users/preferences/language  — USER, ADMIN & VENDOR: update app language
 *
 * VENDOR accounts are denied on /api/users/me (manage business via /api/vendors/*),
 * but are permitted on /api/users/preferences/* to set application language.
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
                ApiResponse.success("User profile retrieved.", "profile.retrieved",
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
                ApiResponse.success("User profile updated.", "profile.updated",
                        userService.updateMe(currentUser(userDetails), request))
        );
    }

    /** USER, ADMIN & VENDOR — returns current user's preferences (e.g. language). */
    @GetMapping("/preferences")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'VENDOR')")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        String lang = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en";
        return ResponseEntity.ok(
                ApiResponse.success("User preferences retrieved.", "preferences.retrieved",
                        new UserPreferenceResponse(lang))
        );
    }

    /** USER, ADMIN & VENDOR — updates user's preferred app language ("en", "hi", "kn"). */
    @PutMapping("/preferences/language")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'VENDOR')")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateLanguage(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LanguagePreferenceRequest request) {
        User user = currentUser(userDetails);
        User updated = userService.updateLanguagePreference(user, request.getLanguage());
        return ResponseEntity.ok(
                ApiResponse.success("Language preference updated.", "preferences.languageUpdated",
                        new UserPreferenceResponse(updated.getPreferredLanguage()))
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}
