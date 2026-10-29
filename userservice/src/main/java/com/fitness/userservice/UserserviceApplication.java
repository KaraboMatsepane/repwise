package com.fitness.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the user service.
 *
 * <p>Manages user registration and profiles backed by PostgreSQL, and registers with the Eureka
 * server as {@code USER-SERVICE} so other services can reach it by name.
 */
@SpringBootApplication
public class UserserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserserviceApplication.class, args);
	}

}
