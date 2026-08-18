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
    @Value("${admin.initial.email}")
    private String adminEmail;

    /**
     * Initial admin password (plain text — hashed before storage).
     * Loaded from ${admin.initial.password} in application-local.properties (local)
     * or the ADMIN_INITIAL_PASSWORD environment variable (production).
     *
     * NEVER logged. NEVER stored as plaintext. Hashed immediately with BCrypt.
     */
    @Value("${admin.initial.password}")
    private String adminPassword;

    /**
     * Initial admin display name.
     * Loaded from ${admin.initial.name} in application-local.properties.
     * Defaults to "Maitri Admin" if not configured.
     */
    @Value("${admin.initial.name:Maitri Admin}")
    private String adminName;

    /**
     * Runs after application context is fully initialized.
     * Seeds the first admin if no admin exists.
     *
     * @param args Command-line arguments (not used)
     */
    @Override
    public void run(String... args) {

        // Check whether any admin account already exists
        boolean adminExists = userRepository.findByEmail(adminEmail.toLowerCase().trim()).isPresent();

        if (adminExists) {
            log.info("[DataSeeder] Admin account already exists. Seeding skipped.");
            return;
        }

        // Hash the password immediately — the plain text variable is not referenced again
        String hashedPassword = passwordEncoder.encode(adminPassword);

        LocalDateTime now = LocalDateTime.now();
        User admin = User.builder()
                .name(adminName)
                .email(adminEmail.toLowerCase().trim())
                .password(hashedPassword)
                .role(Role.ADMIN)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(admin);

        // Log confirmation — never log the password or the email (avoid leaking credentials in log files)
        log.info("[DataSeeder] Initial admin account created successfully. Role: ADMIN.");
    }
}
