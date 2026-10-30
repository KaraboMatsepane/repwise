package com.repwise.aiservice.exception;

/** Thrown when no recommendation exists for an activity. Mapped to HTTP 404. */
public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String activityId) {
        super("No recommendation found for activity: " + activityId);
    }
}
