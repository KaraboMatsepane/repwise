package com.repwise.activityservice.exception;

/** Thrown when an activity cannot be found by id. Mapped to HTTP 404. */
public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException(String activityId) {
        super("Activity not found: " + activityId);
    }
}
