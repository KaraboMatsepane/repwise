package com.repwise.aiservice.model;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specifies the database's guarantee that an activity has at most one recommendation: a unique
 * index on {@code activityId} in the {@code recommendations} collection. The listener's own check
 * skips activities it has already answered, but two copies of a message processed at the same
 * moment (for example by two AI service instances) could both pass that check; the index makes the
 * second save fail instead of storing a duplicate.
 *
 * <p>No MongoDB is needed: Spring Data's own index resolver reads the index definitions from the
 * {@link Recommendation} mapping, exactly as it does when it creates the indexes at startup.
 */
class RecommendationIndexTest {

    private List<IndexDefinition> indexesOfRecommendation() {
        // Configured like Spring Boot's: the Mongo conversions make types such as LocalDateTime
        // simple values instead of entities to map.
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());

        List<IndexDefinition> indexes = new ArrayList<>();
        IndexResolver.create(mappingContext)
                .resolveIndexFor(Recommendation.class)
                .forEach(indexes::add);
        return indexes;
    }

    @Test
    @DisplayName("activityId has a unique index, so a second recommendation for an activity is refused")
    void activityIdIsUnique() {
        assertThat(indexesOfRecommendation())
                .filteredOn(index -> index.getIndexKeys().equals(new Document("activityId", 1)))
                .singleElement()
                .satisfies(index -> assertThat(index.getIndexOptions().get("unique")).isEqualTo(true));
    }
}
