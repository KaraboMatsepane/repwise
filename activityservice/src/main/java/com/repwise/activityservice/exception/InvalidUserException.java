package com.repwise.activityservice.exception;

/** Thrown when the user service says the user does not exist. Mapped to HTTP 404. */
public class InvalidUserException extends RuntimeException {

    public InvalidUserException(String userId) {
        super("User not found: " + userId);
    }
}
