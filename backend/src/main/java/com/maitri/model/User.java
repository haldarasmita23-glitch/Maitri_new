package com.maitri.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * User — MongoDB Document representing a Maitri platform user.
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "users"
 *
 * ─── FIELDS ──────────────────────────────────────────────────────────────────
 *   id         — MongoDB ObjectId (auto-generated, String representation)
 *   name       — Full display name of the user
 *   email      — Login identifier; must be unique across all users
 *   password   — BCrypt-hashed password (NEVER plaintext, NEVER returned in API)
 *   role       — User's role in the system (see Role enum)
 *   active     — Whether this account is enabled (supports soft-disabling accounts)
 *   createdAt  — Timestamp of account creation (auto-set, never updated)
 *   updatedAt  — Timestamp of last update (auto-updated on save)
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   The `password` field has @JsonProperty(access = WRITE_ONLY):
 *     - Jackson CAN deserialize it (e.g., when reading from a request body)
 *     - Jackson will NEVER serialize it (so it NEVER appears in any API response)
 *   This is a defence-in-depth measure. DTOs (UserResponse, AuthResponse) also
 *   never include the password, but this annotation prevents accidental leaks
 *   if User is ever directly serialized.
 *
 *   Email is indexed with @Indexed(unique = true) at the application level.
 *   The MongoDB collection also enforces a unique index at the database level.
 *
 * ─── TIMESTAMPS ──────────────────────────────────────────────────────────────
 *   @CreatedDate and @LastModifiedDate require auditing to be enabled.
 *   We set these manually in AuthService to avoid requiring @EnableMongoAuditing
 *   during Phase 3A. Auditing will be enabled in a later phase.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Document("users") — Maps this class to the MongoDB "users" collection.
 * @Data              — Generates getters, setters, toString, equals, hashCode.
 * @Builder           — Allows: User.builder().email("...").role(Role.USER).build()
 * @NoArgsConstructor — Required for MongoDB deserialization.
 * @AllArgsConstructor — Required for @Builder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     * MongoDB generates the ObjectId; we store it as a String.
     */
    @Id
    private String id;

    /**
     * User's full display name.
     * Example: "Ramesh Kumar", "Priya Nair"
     */
    private String name;

    /**
     * User's email address — used as the login identifier.
     * Must be unique across all users in the system.
     *
     * @Indexed(unique = true): Creates a unique index in MongoDB on this field.
     *   This enforces uniqueness at the database level, not just the application level.
     */
    @Indexed(unique = true)
    private String email;

    /**
     * BCrypt-hashed password.
     *
     * @JsonProperty(access = WRITE_ONLY):
     *   - WRITE_ONLY means Jackson will include this field during deserialization
     *     (reading JSON → object) but EXCLUDE it during serialization (object → JSON).
     *   - This prevents the password hash from EVER appearing in an API response,
     *     even if User is accidentally serialized directly.
     *
     * CRITICAL: This field must NEVER contain a plaintext password.
     * It is always set via: passwordEncoder.encode(rawPassword)
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * User's role in the Maitri platform.
     * Determines what the user is allowed to do.
     * See Role enum for full documentation.
     *
     * Default: Role.USER (regular resident/community member)
     */
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Whether this account is active/enabled.
     * Inactive accounts cannot log in.
     * Default: true (new accounts are active by default).
     *
     * Future use: Admins can deactivate accounts without deleting them.
     */
    @Builder.Default
    private boolean active = true;

    /**
     * Business/contact phone number (Phase 6). Optional.
     */
    private String phone;

    /**
     * User's preferred app language code (Phase 6).
     * Defaults to "en" per the documented schema. Users can change it
     * from their profile page.
     */
    @Builder.Default
    private String preferredLanguage = "en";

    /**
     * User's locality — { area, city } (Phase 6). Optional.
     */
    private UserLocation location;

    /**
     * Profile photo URL (Phase 6). Optional.
     */
    private String profilePhoto;

    /**
     * Timestamp of when this account was created.
     * Set once during registration. Never changed after that.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this account.
     * Updated on every save operation.
     */
    private LocalDateTime updatedAt;
}
