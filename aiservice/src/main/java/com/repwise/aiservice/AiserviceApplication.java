package com.repwise.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the AI service.
 *
 * <p>Serves activity recommendations stored in MongoDB and registers with the Eureka server as
 * {@code AISERVICE}.
 */
@SpringBootApplication
public class AiserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiserviceApplication.class, args);
	}

}
