package com.repwise.userservice.exception;

/**
 * Thrown when a profile must be created for a user whose access token carries no email address.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class MissingEmailException extends RuntimeException {

    public MissingEmailException() {
        super("Your account has no email address, so a profile cannot be created. "
                + "Add an email address to your account and sign in again.");
    }
}
