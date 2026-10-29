package com.fitness.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /api/users/register}.
 *
 * <p>The email must be present and well formed, and the password must be at least six
 * characters. First and last name are optional. Violations are rejected by Bean Validation
 * before the request reaches the service layer.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min= 6, message = "Password must be at least 6 characters")
    private String password;

    private String firstName;
    private String lastName;
}
