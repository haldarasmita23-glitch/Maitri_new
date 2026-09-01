package com.maitri.dto.user;

import com.maitri.model.UserLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserUpdateRequest — DTO for updating the current user's profile.
 *
 * Used by:
 *   PUT /api/users/me — authenticated USER/ADMIN updates their own profile
 *
 * ─── WHAT IS EDITABLE ────────────────────────────────────────────────────────
 *   - name:              Display name (required, matches registration rules)
 *   - phone:             Contact phone (optional, 10-digit Indian mobile if set)
 *   - preferredLanguage: App language code (optional, e.g. "en", "hi", "kn")
 *   - location:          Locality { area, city } (optional)
 *   - profilePhoto:      Profile photo URL (optional)
 *
 * ─── WHAT IS NOT EDITABLE ────────────────────────────────────────────────────
 *   - email:   Login identifier — immutable. Changing it is out of scope for
 *              Phase 6 (would require re-verification + token re-issuance).
 *   - password: Changed via a dedicated password endpoint (future phase), not
 *              here.
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   There is deliberately NO `role`, `active`, `email`, or `id` field here.
 *   A user can NEVER escalate their own privileges or alter their own status
 *   through profile updates. Unknown JSON fields (e.g. a client sneaking in
 *   "role": "ADMIN") are ignored by Jackson, so this endpoint cannot be used
 *   for privilege escalation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    /**
     * User's full display name.
     * Must not be blank; between 2 and 50 characters (same rules as sign-up).
     */
    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters.")
    private String name;

    /**
     * Contact phone number. Optional.
     * If provided, must be a valid 10-digit Indian mobile number.
     */
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Phone must be a valid 10-digit Indian mobile number.")
    private String phone;

    /**
     * Preferred app language code. Optional.
     * Must be one of the supported codes: "en", "hi", "kn".
     */
    @Pattern(regexp = "^(en|hi|kn)$", message = "Language must be one of: en, hi, kn.")
    @Size(max = 10, message = "Preferred language must be at most 10 characters.")
    private String preferredLanguage;

    /**
     * Locality — { area, city }. Optional.
     * Both sub-fields are optional strings (max 100 characters each).
     */
    private UserLocation location;

    /**
     * Profile photo URL. Optional.
     * Length-capped to prevent abuse; no strict URL regex so users can also
     * reference relative or data URLs.
     */
    @Size(max = 500, message = "Profile photo URL must be at most 500 characters.")
    private String profilePhoto;
}
