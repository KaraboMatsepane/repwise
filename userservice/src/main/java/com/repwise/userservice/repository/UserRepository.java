package com.repwise.userservice.repository;

import com.repwise.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User} entities stored in PostgreSQL.
 *
 * <p>Standard CRUD operations are inherited from {@link JpaRepository}, keyed by the user's
 * string identifier.
 */
public interface UserRepository extends JpaRepository<User, String> {
}
