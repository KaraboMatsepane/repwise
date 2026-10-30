package com.repwise.userservice.exception;

/** Thrown when a user cannot be found by id. Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}
