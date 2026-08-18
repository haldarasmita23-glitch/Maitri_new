package com.maitri.model;

/**
 * Role — User Role Enum for Maitri.
 *
 * ─── ROLE HIERARCHY ──────────────────────────────────────────────────────────
 *
 *  USER       — A regular resident of Peenya / Nagasandra.
 *               Can browse vendors, write reviews, use community features.
 *
 *  VENDOR     — A local business owner.
 *               Can manage their own vendor profile, respond to reviews.
 *               Self-registerable via POST /api/auth/register with role=VENDOR.
 *
 *  ADMIN      — A Maitri platform administrator.
 *               Can manage users, vendors, reviews, and platform content.
 *               Cannot be assigned via public registration.
 *               Created via admin seeding (DataSeeder) or by a SUPER_ADMIN.
 *
 *  SUPER_ADMIN — The platform owner / technical super-administrator.
 *                Has all permissions. Created only via DataSeeder or DB directly.
 *
 * ─── SECURITY RULE ───────────────────────────────────────────────────────────
 *  Public registration (POST /api/auth/register) may only produce USER or VENDOR.
 *  Any attempt to register as ADMIN or SUPER_ADMIN will be rejected with 403.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Spring Security prefix convention:
 *   Spring Security's @PreAuthorize("hasRole('ADMIN')") automatically prepends
 *   "ROLE_" to the role name. So internally, the granted authority stored in
 *   the SecurityContext will be "ROLE_ADMIN", "ROLE_USER", etc.
 *   The enum value itself (e.g., ADMIN) is stored in MongoDB as a plain string.
 */
public enum Role {

    /** Regular resident / community member */
    USER,

    /** Local business owner */
    VENDOR,

    /** Platform administrator */
    ADMIN,

    /** Platform super-administrator (highest privilege) */
    SUPER_ADMIN
}
