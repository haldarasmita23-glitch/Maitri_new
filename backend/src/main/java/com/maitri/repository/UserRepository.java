package com.maitri.repository;

import com.maitri.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User Repository — Spring Data MongoDB repository for the User document.
 *
 * ─── HOW SPRING DATA WORKS ───────────────────────────────────────────────────
 *   Spring Data generates the implementation of this interface at runtime.
 *   We do NOT write any query code — Spring derives queries from method names.
 *
 *   Example:
 *     findByEmail(String email)
 *     → Spring generates: db.users.findOne({ email: "..." })
 *
 *   We inherit standard CRUD operations from MongoRepository:
 *     - save(User)      — insert or update
 *     - findById(id)    — find by MongoDB _id
 *     - findAll()       — list all users (use carefully — not for large collections)
 *     - deleteById(id)  — delete a user
 *     - count()         — count all users
 *
 * ─── EMAIL UNIQUENESS ────────────────────────────────────────────────────────
 *   Email uniqueness is enforced at TWO levels:
 *     1. Application level: existsByEmail() is called in AuthService before saving
 *     2. Database level: @Indexed(unique=true) on User.email creates a MongoDB
 *        unique index — a duplicate insert will throw DuplicateKeyException
 *
 *   Both layers are intentional (defence in depth).
 *   The application check gives a clean error message.
 *   The database index prevents race conditions.
 *
 * @Repository: Marks this as a Spring-managed repository component.
 *   Also causes Spring to translate MongoDB exceptions into Spring's
 *   DataAccessException hierarchy for consistent error handling.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Find a user by their email address.
     *
     * Used during:
     *   - Login: to load the user and verify their password
     *   - JWT filter: to reload UserDetails for each authenticated request
     *   - /api/auth/me: to get the current user's profile
     *
     * Returns Optional.empty() if no user exists with that email.
     * NEVER throws an exception for missing users — callers handle the Optional.
     *
     * @param email The email address to search for
     * @return An Optional containing the User if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether a user with the given email already exists.
     *
     * Used during registration to reject duplicate email addresses
     * before attempting to insert a new user document.
     *
     * More efficient than findByEmail() for existence checks — MongoDB
     * only needs to confirm existence, not return the full document.
     *
     * @param email The email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);
}
