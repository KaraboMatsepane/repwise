package com.repwise.userservice.exception;

/**
 * Thrown when a first-time user's profile cannot be created because another profile already has
 * the same email address, for example a Keycloak account that was deleted and created again with
 * a new id. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException(Throwable cause) {
        super("Your email address is already used by another profile, so a profile cannot be "
                + "created for your account.", cause);
    }
}
