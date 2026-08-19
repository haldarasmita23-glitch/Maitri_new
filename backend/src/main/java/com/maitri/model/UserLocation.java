package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserLocation — Embeddable location for a user profile (Phase 6).
 *
 * Stored inside the `users` document as a nested object matching the
 * documented schema: location = { area, city }.
 *
 *   area — Neighbourhood / locality (e.g. "Peenya", "Nagasandra")
 *   city — City (e.g. "Bengaluru")
 *
 * Both fields are optional — a user can update them later from their
 * profile page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocation {

    private String area;

    private String city;
}
