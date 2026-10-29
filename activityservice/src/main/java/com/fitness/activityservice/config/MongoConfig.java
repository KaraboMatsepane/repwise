package com.fitness.activityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables MongoDB auditing so that {@code @CreatedDate} and {@code @LastModifiedDate} fields on
 * documents such as {@link com.fitness.activityservice.model.Activity} are populated
 * automatically on save.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
