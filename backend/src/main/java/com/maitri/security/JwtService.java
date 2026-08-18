package com.maitri.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Service — Creates, validates, and parses JSON Web Tokens.
 *
 * ─── WHAT IS A JWT? ──────────────────────────────────────────────────────────
 *   A JWT (JSON Web Token) is a signed, self-contained token that proves
 *   a user's identity. It has three Base64-encoded parts separated by dots:
 *     header.payload.signature
 *
 *   header:    Algorithm info (we use HS256 — HMAC with SHA-256)
 *   payload:   Claims (subject=email, role, iat=issued-at, exp=expiration)
 *   signature: HMAC(header + "." + payload, secret_key)
 *
 *   The server validates the signature on every request. If valid, the user
 *   is authenticated without any server-side session storage.
 *
 * ─── CLAIMS STORED IN TOKEN ──────────────────────────────────────────────────
 *   sub  (subject)     : user's email address (the login identifier)
 *   role               : user's role (e.g., "USER", "ADMIN")
 *   iat  (issued-at)   : Unix timestamp when the token was created
 *   exp  (expiration)  : Unix timestamp when the token expires
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - The secret key is loaded from properties (never hardcoded)
 *   - We use JJWT 0.12.3 — the modern API compatible with Spring Boot 3.x
 *   - Tokens are signed with HS256 (symmetric HMAC). The same secret is used
 *     for both signing (during login) and verification (per request).
 *   - Expired tokens are rejected at the filter level.
 *   - We do NOT store tokens in MongoDB — tokens are stateless.
 *
 * ─── JJWT 0.12.x API NOTES ──────────────────────────────────────────────────
 *   The 0.12.x API changed significantly from 0.11.x:
 *     OLD (0.11.x): Jwts.parserBuilder().setSigningKey(key).build()
 *     NEW (0.12.x): Jwts.parser().verifyWith(key).build()
 *   We use the 0.12.x API exclusively.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Slf4j: Provides a `log` field for SLF4J logging (via Lombok).
 */
@Service
@Slf4j
public class JwtService {

    /**
     * The JWT signing secret.
     *
     * Loaded from ${jwt.secret} in application-local.properties (local dev)
     * or the JWT_SECRET environment variable in production.
     *
     * MUST be at least 256 bits (32 characters) long for HS256.
     * NEVER hardcoded. NEVER committed to Git.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Token expiration duration in milliseconds.
     *
     * Loaded from ${jwt.expiration.ms} — typically 86400000 (24 hours).
     * Configurable per environment without code changes.
     */
    @Value("${jwt.expiration.ms}")
    private long jwtExpirationMs;

    // ─── Token Generation ─────────────────────────────────────────────────────

    /**
     * Generates a JWT token for an authenticated user.
     *
     * Called after successful login or registration.
     * Includes the user's email (as subject) and role (as a custom claim).
     *
     * @param userDetails Spring Security's UserDetails (email is the username)
     * @return Signed JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();

        // Extract role from the user's granted authorities.
        // Authorities are stored as "ROLE_USER", "ROLE_ADMIN", etc.
        // We strip the "ROLE_" prefix to store the clean role name in the JWT.
        userDetails.getAuthorities().stream()
                .findFirst()
                .ifPresent(authority -> {
                    String roleName = authority.getAuthority();
                    // Strip "ROLE_" prefix if present
                    if (roleName.startsWith("ROLE_")) {
                        roleName = roleName.substring(5);
                    }
                    extraClaims.put("role", roleName);
                });

        return buildToken(extraClaims, userDetails);
    }

    /**
     * Builds and signs the JWT token.
     *
     * @param extraClaims Additional claims to include (e.g., role)
     * @param userDetails The authenticated user (email as subject)
     * @return Signed JWT string
     */
    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .claims(extraClaims)                                    // extra claims (role)
                .subject(userDetails.getUsername())                     // email
                .issuedAt(new Date(now))                               // iat
                .expiration(new Date(now + jwtExpirationMs))           // exp
                .signWith(getSigningKey())                              // HS256 signature
                .compact();
    }

    // ─── Token Validation ─────────────────────────────────────────────────────

    /**
     * Validates a JWT token against a specific user's details.
     *
     * Checks:
     *   1. The email in the token matches the UserDetails username
     *   2. The token has not expired
     *
     * Does NOT check whether the user still exists in the database —
     * that check is performed in JwtAuthenticationFilter.
     *
     * @param token       The JWT string from the Authorization header
     * @param userDetails The user to validate against
     * @return true if the token is valid for this user, false otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("JWT token is invalid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the token's expiration date is in the past.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ─── Claims Extraction ────────────────────────────────────────────────────

    /**
     * Extracts the email address (subject claim) from the token.
     *
     * @param token JWT string
     * @return The email address stored in the token's 'sub' claim
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the role claim from the token.
     *
     * @param token JWT string
     * @return The role string (e.g., "USER", "ADMIN")
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extracts the expiration date from the token.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor — applies a function to the full claims map.
     *
     * @param token          JWT string
     * @param claimsResolver Function to apply to the parsed claims
     * @param <T>            Return type of the resolver function
     * @return The extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the JWT and returns all claims.
     *
     * Uses JJWT 0.12.x API: Jwts.parser().verifyWith(key).build()
     *
     * Throws JwtException (including ExpiredJwtException) if the token
     * is invalid, malformed, or expired.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ─── Key Construction ─────────────────────────────────────────────────────

    /**
     * Derives an HS256-compatible SecretKey from the configured secret string.
     *
     * Keys.hmacShaKeyFor() converts the raw bytes into a proper javax.crypto.SecretKey.
     * The secret must be at least 256 bits (32 bytes) for HS256.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
