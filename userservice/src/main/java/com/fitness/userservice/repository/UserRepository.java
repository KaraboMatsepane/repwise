package com.fitness.userservice.repository;

import com.fitness.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User} entities stored in PostgreSQL.
 *
 * <p>Standard CRUD operations are inherited from {@link JpaRepository}, keyed by the user's
 * string identifier.
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Checks whether a user is already registered with the given email address.
     *
     * @param email the email address to look for
     * @return {@code true} if a user with that email exists
     */
    boolean existsByEmail(String email);
}
