package com.repwise.activityservice.exception;

/**
 * Thrown when the user service rejects the caller's forwarded access token with {@code 401}, for
 * example because it expired between reaching this service and the profile check. Mapped to HTTP
 * 401, so the client knows to get a new token instead of retrying.
 */
public class ForwardedTokenRejectedException extends RuntimeException {

    public ForwardedTokenRejectedException(Throwable cause) {
        super("The user service rejected the caller's access token", cause);
    }
}
