package com.repwise.aiservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables MongoDB auditing so that the {@code @CreatedDate} field on
 * {@link com.repwise.aiservice.model.Recommendation} is populated automatically on save.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
