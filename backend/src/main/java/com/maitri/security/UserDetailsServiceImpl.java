package com.maitri.security;

import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsService Implementation — Bridges Spring Security and MongoDB.
 *
 * ─── PURPOSE ─────────────────────────────────────────────────────────────────
 *   Spring Security needs a way to load user details (password, authorities)
 *   by username (in our case, email). This class provides that bridge.
 *
 *   It is used by:
 *     1. JwtAuthenticationFilter — loads user from DB to validate JWT on each request
 *     2. DaoAuthenticationProvider — loads user to verify password during login
 *
 * ─── UserDetails CONTRACT ────────────────────────────────────────────────────
 *   Spring Security requires UserDetails to have:
 *     - getUsername()       → the login identifier (email)
 *     - getPassword()       → the stored BCrypt hash
 *     - getAuthorities()    → granted roles (e.g., ROLE_USER, ROLE_ADMIN)
 *     - isAccountNonExpired()  → true (we don't expire accounts in Phase 3)
 *     - isAccountNonLocked()   → true (we don't lock accounts in Phase 3)
 *     - isCredentialsNonExpired() → true
 *     - isEnabled()         → mapped to User.active field
 *
 * ─── AUTHORITY FORMAT ────────────────────────────────────────────────────────
 *   Spring Security expects authorities in the format "ROLE_<rolename>".
 *   So Role.USER → "ROLE_USER", Role.ADMIN → "ROLE_ADMIN", etc.
 *
 *   This allows @PreAuthorize("hasRole('ADMIN')") and
 *   hasRole(\"ROLE_ADMIN\") to work correctly in SecurityConfig.
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - UsernameNotFoundException is thrown when no user exists with the given email.
 *     Spring Security catches this and converts it to an authentication failure.
 *   - We do NOT log the email on failures to avoid leaking information in logs.
 *
 * @RequiredArgsConstructor: Generates constructor injection for userRepository.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user's details from MongoDB by their email address.
     *
     * Called by Spring Security during:
     *   - Login (DaoAuthenticationProvider calls this to get the stored password)
     *   - JWT-authenticated requests (JwtAuthenticationFilter calls this to
     *     load a full UserDetails object from the email in the JWT)
     *
     * @param email The email address to look up (Spring Security calls this "username")
     * @return A UserDetails object with the user's credentials and authorities
     * @throws UsernameNotFoundException if no user exists with the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Load user from MongoDB
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email
                ));

        // Build the Spring Security authority from the user's Role enum.
        // Format: "ROLE_" + role name (e.g., "ROLE_USER", "ROLE_ADMIN")
        // This naming convention is required by Spring Security for hasRole() checks.
        String authorityName = "ROLE_" + user.getRole().name();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())              // email is the "username"
                .password(user.getPassword())           // BCrypt hash
                .authorities(List.of(new SimpleGrantedAuthority(authorityName)))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isActive())             // inactive users cannot log in
                .build();
    }
}
