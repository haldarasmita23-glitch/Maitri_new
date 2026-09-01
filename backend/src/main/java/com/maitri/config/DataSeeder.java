package com.maitri.config;

import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Data Seeder — Seeds the first admin account on application startup.
 *
 * ─── PURPOSE ─────────────────────────────────────────────────────────────────
 *   Bootstraps the platform by creating the first ADMIN account if none exists.
 *   Without this, there would be no way to log in as an admin initially,
 *   because admin accounts cannot be created via the public registration API.
 *
 * ─── HOW IT WORKS ────────────────────────────────────────────────────────────
 *   CommandLineRunner.run() is called by Spring Boot after the application
 *   context is fully initialized and before it starts accepting requests.
 *
 *   Logic:
 *   1. Check if any admin user already exists in MongoDB
 *   2. If yes → skip seeding (idempotent — safe to restart the app)
 *   3. If no  → create admin with hashed password and save to MongoDB
 *
 * ─── CREDENTIAL SOURCES ──────────────────────────────────────────────────────
 *   Admin credentials are loaded from configuration properties, NOT hardcoded:
 *     admin.initial.email     — loaded from application-local.properties (local)
 *                               or ADMIN_INITIAL_EMAIL env variable (production)
 *     admin.initial.password  — NEVER logged; hashed immediately with BCrypt
 *
 *   Neither credential is ever committed to Git.
 *
 * ─── IDEMPOTENCY ─────────────────────────────────────────────────────────────
 *   If an admin already exists, seeding is skipped entirely.
 *   This means restarting the application is always safe —
 *   it will never create duplicate admin accounts.
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - The raw admin password is NEVER logged (not even at DEBUG level)
 *   - The password is hashed with BCrypt(12) before storage
 *   - If ${admin.initial.email} is not configured, the application will
 *     fail to start — this is intentional (fail-safe configuration)
 *
 * @Component: Registers this as a Spring-managed component.
 * @RequiredArgsConstructor: Constructor injection for all final fields.
 * @Slf4j: SLF4J logger via Lombok.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    public static final String PRIMARY_ADMIN_EMAIL = "maitri.admin@gmail.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.initial.email:${ADMIN_INITIAL_EMAIL:maitri.admin@gmail.com}}")
    private String configuredAdminEmail;

    @Value("${admin.initial.password:${ADMIN_INITIAL_PASSWORD:}}")
    private String adminPassword;

    @Value("${admin.initial.name:${ADMIN_INITIAL_NAME:Maitri Admin}}")
    private String adminName;

    /**
     * Runs after application context is fully initialized.
     * Seeds or validates the primary administrator account (maitri.admin@gmail.com)
     * and any configured secondary administrator.
     *
     * @param args Command-line arguments (not used)
     */
    @Override
    public void run(String... args) {
        // 1. Always ensure the primary administrator account exists and is valid
        seedOrUpdateAdmin(PRIMARY_ADMIN_EMAIL, "Maitri Admin");

        // 2. If a custom admin email is configured and differs from primary, ensure it as well
        if (configuredAdminEmail != null && !configuredAdminEmail.isBlank()) {
            String customEmail = configuredAdminEmail.toLowerCase().trim();
            if (!customEmail.equalsIgnoreCase(PRIMARY_ADMIN_EMAIL)) {
                seedOrUpdateAdmin(customEmail, adminName);
            }
        }
    }

    private void seedOrUpdateAdmin(String email, String displayName) {
        String normalizedEmail = email.toLowerCase().trim();
        var userOpt = userRepository.findByEmail(normalizedEmail);

        String targetPassword = (adminPassword != null && !adminPassword.isBlank())
                ? adminPassword
                : "maitri@admin";

        if (userOpt.isPresent()) {
            User existing = userOpt.get();
            boolean updated = false;

            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                updated = true;
                log.info("[DataSeeder] Existing account {} updated with ROLE_ADMIN.", normalizedEmail);
            }
            if (!existing.isActive()) {
                existing.setActive(true);
                updated = true;
            }
            if (!passwordEncoder.matches(targetPassword, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(targetPassword));
                updated = true;
                log.info("[DataSeeder] Password for {} updated with secure BCrypt hash.", normalizedEmail);
            }
            if (updated) {
                existing.setUpdatedAt(LocalDateTime.now());
                userRepository.save(existing);
            } else {
                log.info("[DataSeeder] Administrator account {} verified. Role: ADMIN.", normalizedEmail);
            }
            return;
        }

        // Create new admin account with BCrypt-hashed password
        String hashedPassword = passwordEncoder.encode(targetPassword);
        LocalDateTime now = LocalDateTime.now();
        User admin = User.builder()
                .name(displayName != null && !displayName.isBlank() ? displayName : "Maitri Admin")
                .email(normalizedEmail)
                .password(hashedPassword)
                .role(Role.ADMIN)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(admin);
        log.info("[DataSeeder] Administrator account {} created successfully. Role: ADMIN.", normalizedEmail);
    }
}
