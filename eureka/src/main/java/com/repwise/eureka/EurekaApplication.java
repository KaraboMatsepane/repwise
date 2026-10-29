package com.repwise.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Entry point of the Eureka service registry.
 *
 * <p>Every other microservice in the Repwise platform registers itself with this server and uses
 * it to locate its peers by logical name (for example {@code USER-SERVICE}) instead of by host
 * and port. The server listens on port 8761 and is configured not to register with itself or
 * fetch a registry, because it is the registry.
 *
 * <p>It must be started before the other services so they can register on startup.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaApplication.class, args);
	}

}
