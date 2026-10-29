package com.repwise.activityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables MongoDB auditing so that {@code @CreatedDate} and {@code @LastModifiedDate} fields on
 * documents such as {@link com.repwise.activityservice.model.Activity} are populated
 * automatically on save.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
