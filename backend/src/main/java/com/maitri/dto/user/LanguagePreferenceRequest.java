package com.maitri.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LanguagePreferenceRequest — DTO for updating user's app language preference.
 *
 * Supported language codes:
 *   - "en": English
 *   - "hi": हिन्दी (Hindi)
 *   - "kn": ಕನ್ನಡ (Kannada)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguagePreferenceRequest {

    @NotBlank(message = "Language code is required.")
    @Pattern(regexp = "^(en|hi|kn)$", message = "Language must be one of: en, hi, kn.")
    private String language;
}
