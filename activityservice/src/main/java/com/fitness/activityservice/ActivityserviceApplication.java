package com.fitness.activityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the activity service.
 *
 * <p>Records and retrieves workouts backed by MongoDB, validates users through the user service,
 * and registers with the Eureka server as {@code ACTIVITYSERVICE}.
 */
@SpringBootApplication
public class ActivityserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActivityserviceApplication.class, args);
	}

}
