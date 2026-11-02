package com.repwise.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the user service.
 *
 * <p>Stores user profiles in PostgreSQL. Identity (credentials, login, tokens) belongs to
 * Keycloak: this service only verifies Keycloak access tokens and creates a user's profile the
 * first time they call {@code GET /api/users/me}. It registers with the Eureka server as
 * {@code USER-SERVICE} so other services can reach it by name.
 */
@SpringBootApplication
public class UserserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserserviceApplication.class, args);
	}

}
