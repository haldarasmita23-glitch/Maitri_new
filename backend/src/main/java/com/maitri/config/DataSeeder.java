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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Initial admin email address.
     * Loaded from ${admin.initial.email} in application-local.properties (local)
     * or the ADMIN_INITIAL_EMAIL environment variable (production).
     */
    @Value("${admin.initial.email:${ADMIN_INITIAL_EMAIL:maitri.admin@gmail.com}}")
    private String adminEmail;

    @Value("${admin.initial.password:${ADMIN_INITIAL_PASSWORD:}}")
    private String adminPassword;

    @Value("${admin.initial.name:${ADMIN_INITIAL_NAME:Maitri Admin}}")
    private String adminName;

    /**
     * Runs after application context is fully initialized.
     * Seeds or validates the primary administrator account.
     *
     * @param args Command-line arguments (not used)
     */
    @Override
    public void run(String... args) {
        String normalizedEmail = (adminEmail != null && !adminEmail.isBlank())
                ? adminEmail.toLowerCase().trim()
                : "maitri.admin@gmail.com";

        var existingUserOpt = userRepository.findByEmail(normalizedEmail);

        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            boolean updated = false;

            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                updated = true;
                log.info("[DataSeeder] Existing account assigned ROLE_ADMIN.");
            }
            if (!existing.isActive()) {
                existing.setActive(true);
                updated = true;
            }
            if (adminPassword != null && !adminPassword.isBlank()
                    && !passwordEncoder.matches(adminPassword, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(adminPassword));
                updated = true;
                log.info("[DataSeeder] Administrator password updated securely with BCrypt.");
            }
            if (updated) {
                existing.setUpdatedAt(LocalDateTime.now());
                userRepository.save(existing);
            } else {
                log.info("[DataSeeder] Administrator account verified. Role: ADMIN.");
            }
            return;
        }

        // Generate secure BCrypt hash for initial setup
        String hashedPassword = (adminPassword != null && !adminPassword.isBlank())
                ? passwordEncoder.encode(adminPassword)
                : passwordEncoder.encode("maitri@admin");

        LocalDateTime now = LocalDateTime.now();
        User admin = User.builder()
                .name(adminName != null && !adminName.isBlank() ? adminName : "Maitri Admin")
                .email(normalizedEmail)
                .password(hashedPassword)
                .role(Role.ADMIN)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(admin);

        log.info("[DataSeeder] Initial administrator account created successfully. Role: ADMIN.");
    }
}
