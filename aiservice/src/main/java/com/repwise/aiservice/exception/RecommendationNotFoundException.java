package com.repwise.aiservice.exception;

/**
 * Thrown when no recommendation exists for an activity, or it belongs to another user. Mapped to
 * HTTP 404.
 */
public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String activityId) {
        super("No recommendation found for activity: " + activityId);
    }
}
