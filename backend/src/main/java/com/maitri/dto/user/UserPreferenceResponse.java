package com.maitri.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserPreferenceResponse — safe DTO projecting user preference settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceResponse {

    /**
     * App language preference code: "en", "hi", or "kn".
     */
    private String preferredLanguage;
}
