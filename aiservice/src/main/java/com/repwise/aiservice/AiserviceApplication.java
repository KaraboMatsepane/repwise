package com.repwise.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the AI service.
 *
 * <p>Generates activity recommendations with Gemini from messages published by the activity
 * service, stores them in MongoDB and serves them. Requests must carry a valid Keycloak access
 * token, and a user can only read their own recommendations. It registers with the Eureka server
 * as {@code AI-SERVICE}.
 */
@SpringBootApplication
public class AiserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiserviceApplication.class, args);
	}

}
