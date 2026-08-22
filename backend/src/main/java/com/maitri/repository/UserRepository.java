package com.maitri.repository;

import com.maitri.model.Role;
import com.maitri.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /**
     * Find users by role.
     *
     * @param role The role to filter by
     * @return List of users with the given role
     */
    List<User> findByRole(Role role);
}