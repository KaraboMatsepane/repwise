package com.repwise.activityservice.exception;

/** Thrown when the user service cannot be reached or answers with an error. Mapped to HTTP 503. */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
