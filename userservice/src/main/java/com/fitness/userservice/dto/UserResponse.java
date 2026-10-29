package com.fitness.userservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response body describing a user, returned by the registration and profile endpoints.
 *
 * <p>This is a flat copy of the persisted {@link com.fitness.userservice.model.User} fields so
 * that the API contract is independent of the database entity.
 */
@Data
public class UserResponse {

    private String id;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
