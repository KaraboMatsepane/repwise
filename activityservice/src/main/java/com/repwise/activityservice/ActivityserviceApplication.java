package com.repwise.activityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the activity service.
 *
 * <p>Records and retrieves workouts backed by MongoDB. Requests must carry a valid Keycloak access
 * token, and the token's subject is the owner of every activity. It checks that the user has a
 * profile through the user service and registers with the Eureka server as
 * {@code ACTIVITY-SERVICE}.
 */
@SpringBootApplication
public class ActivityserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActivityserviceApplication.class, args);
	}

}
