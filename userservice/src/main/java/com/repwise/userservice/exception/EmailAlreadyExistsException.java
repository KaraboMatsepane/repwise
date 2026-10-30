package com.repwise.userservice.exception;

/** Thrown when registering with an email that is already taken. Mapped to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
}
