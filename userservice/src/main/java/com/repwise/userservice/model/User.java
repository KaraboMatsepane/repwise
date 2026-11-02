package com.repwise.userservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity for a user's profile, stored in the {@code users} table.
 *
 * <p>The identifier is the user's Keycloak id (the token's {@code sub} claim), not generated
 * here, and the email is unique. No credentials are stored: Keycloak owns them. New users default
 * to {@link UserRole#USER}. Creation and update timestamps are maintained by Hibernate.
 */
@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
