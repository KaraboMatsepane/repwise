package com.repwise.aiservice.service;

import com.repwise.aiservice.config.RabbitMqConfig;
import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Specifies what happens to an activity message that cannot be processed, against a real RabbitMQ
 * broker started in a throwaway Docker container.
 *
 * <p>The contract:
 * <ul>
 *   <li>A message is attempted at most {@value #MAX_ATTEMPTS} times in total (the first delivery
 *       plus retries), and is then moved to the dead-letter queue instead of being requeued
 *       forever.</li>
 *   <li>A failure that goes away on a retry is not dead-lettered.</li>
 *   <li>A message that cannot even be converted to an activity is dead-lettered immediately, and
 *       is never lost.</li>
 *   <li>A dead-lettered message keeps its original content.</li>
 * </ul>
 *
 * <p>Only the infrastructure is real: the broker, the queue topology from {@link RabbitMqConfig},
 * the listener and Spring's listener container with its retry settings, which are read from the
 * application's own {@code src/main/resources/application.properties}. That file is loaded here as
 * plain properties: its {@code spring.config.import} line is deliberately inert, so no config server
 * is needed, while any listener settings it contains apply exactly as they would when shipped. The recommendation
 * generator and the database are Mockito mocks, so no Gemini call and no MongoDB are involved. The
 * wait between retries is shortened to keep the tests fast; the number of attempts is not touched.
 *
 * <p>Needs Docker. Without it, the tests are skipped.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(ActivityMessageListenerDeadLetterTest.TestApplication.class)
@TestPropertySource(
        locations = "classpath:application.properties",
        properties = {
                // The names normally supplied by the config server.
                "rabbitmq.queue.name=" + ActivityMessageListenerDeadLetterTest.QUEUE,
                "rabbitmq.exchange.name=" + ActivityMessageListenerDeadLetterTest.EXCHANGE,
                "rabbitmq.routing.key=" + ActivityMessageListenerDeadLetterTest.ROUTING_KEY,
                "rabbitmq.dlx.name=" + ActivityMessageListenerDeadLetterTest.DEAD_LETTER_EXCHANGE,
                "rabbitmq.dlq.name=" + ActivityMessageListenerDeadLetterTest.DEAD_LETTER_QUEUE,
                "rabbitmq.dlq.routing.key=" + ActivityMessageListenerDeadLetterTest.DEAD_LETTER_ROUTING_KEY,
                // Speed only: shorten the pause between retries. The number of attempts comes from
                // the application's own configuration.
                "spring.rabbitmq.listener.simple.retry.initial-interval=20ms"
        })
class ActivityMessageListenerDeadLetterTest {

    static final String QUEUE = "activity.queue";
    static final String EXCHANGE = "activity.exchange";
    static final String ROUTING_KEY = "activity.tracking";
    static final String DEAD_LETTER_EXCHANGE = "activity.dlx";
    static final String DEAD_LETTER_QUEUE = "activity.dlq";
    static final String DEAD_LETTER_ROUTING_KEY = "activity.failed";

    /** The first delivery plus its retries. */
    static final int MAX_ATTEMPTS = 3;

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @DynamicPropertySource
    static void brokerLocation(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    /** Loads only the RabbitMQ pieces: the real topology, the real listener, Boot's AMQP setup. */
    @Configuration
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import({RabbitMqConfig.class, ActivityMessageListener.class})
    static class TestApplication {
    }

    @MockitoBean
    private ActivityAiService activityAiService;
    @MockitoBean
    private RecommendationRepository recommendationRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void emptyTheQueues() {
        for (String queue : List.of(QUEUE, DEAD_LETTER_QUEUE)) {
            if (amqpAdmin.getQueueInfo(queue) != null) {
                amqpAdmin.purgeQueue(queue, false);
            }
        }
    }

    // ---- helpers ----

    private void publishActivity(String id) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, Map.of(
                "id", id,
                "userId", "alice-id",
                "type", "RUNNING",
                "duration", 37,
                "caloriesBurned", 413,
                "startTime", "2026-11-02T06:30:00"));
    }

    private void assertDeadLetterQueueExists() {
        assertThat(amqpAdmin.getQueueInfo(DEAD_LETTER_QUEUE))
                .as("the dead-letter queue '%s' should have been declared on the broker", DEAD_LETTER_QUEUE)
                .isNotNull();
    }

    private int callsToTheRecommendationGenerator() {
        return Mockito.mockingDetails(activityAiService).getInvocations().size();
    }

    private Message awaitDeadLetter() {
        Message message = rabbitTemplate.receive(DEAD_LETTER_QUEUE, PATIENCE.toMillis());
        assertThat(message)
                .as("the message should have reached the dead-letter queue within %s; the recommendation "
                        + "generator was called %d times in that time", PATIENCE, callsToTheRecommendationGenerator())
                .isNotNull();
        return message;
    }

    private long messagesWaitingIn(String queue) {
        QueueInformation info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    private Recommendation someRecommendation() {
        return Recommendation.builder().id("rec-1").activityId("act-1").userId("alice-id").build();
    }

    // ---- a message that keeps failing ----

    @Test
    @DisplayName("a message that keeps failing is attempted 3 times in total, then moved to the dead-letter queue")
    void failingMessageIsAttemptedThreeTimesThenDeadLettered() throws InterruptedException {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class)))
                .thenThrow(new IllegalStateException("Gemini reply was not JSON"));

        publishActivity("act-1");
        awaitDeadLetter();

        // Give a (wrong) endless retry loop time to show itself before counting.
        Thread.sleep(500);
        verify(activityAiService, times(MAX_ATTEMPTS)).generateRecommendation(any(Activity.class));
    }

    @Test
    @DisplayName("once dead-lettered, the message is gone from the activity queue and nothing is saved")
    void deadLetteredMessageLeavesTheActivityQueue() {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class)))
                .thenThrow(new IllegalStateException("Gemini reply was not JSON"));

        publishActivity("act-1");
        awaitDeadLetter();

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(messagesWaitingIn(QUEUE)).isZero());
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    @DisplayName("the dead-lettered message keeps its original content and records where it came from")
    void deadLetteredMessageKeepsItsContent() {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class)))
                .thenThrow(new IllegalStateException("Gemini reply was not JSON"));

        publishActivity("act-42");
        Message dead = awaitDeadLetter();

        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8))
                .contains("act-42").contains("alice-id").contains("RUNNING");
        // RabbitMQ adds an x-death header describing every dead-lettering, so the failure can be traced.
        Object xDeath = dead.getMessageProperties().getHeaders().get("x-death");
        assertThat(xDeath).as("the x-death header").isInstanceOf(List.class);
        Map<?, ?> firstDeath = (Map<?, ?>) ((List<?>) xDeath).get(0);
        assertThat(firstDeath.get("queue")).isEqualTo(QUEUE);
        assertThat(firstDeath.get("reason")).isEqualTo("rejected");
    }

    @Test
    @DisplayName("a failure while saving is retried and dead-lettered in the same way")
    void saveFailureIsRetriedThenDeadLettered() {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class))).thenReturn(someRecommendation());
        when(recommendationRepository.save(any(Recommendation.class))).thenThrow(new RuntimeException("database is down"));

        publishActivity("act-1");
        awaitDeadLetter();

        verify(recommendationRepository, times(MAX_ATTEMPTS)).save(any(Recommendation.class));
    }

    // ---- a failure that goes away ----

    @Test
    @DisplayName("a failure that clears up on a retry is processed normally and is not dead-lettered")
    void transientFailureIsRetriedAndSucceeds() {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class)))
                .thenThrow(new IllegalStateException("Gemini timed out"))
                .thenThrow(new IllegalStateException("Gemini timed out"))
                .thenReturn(someRecommendation());
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(call -> call.getArgument(0));

        publishActivity("act-1");

        await().atMost(PATIENCE).untilAsserted(() ->
                verify(recommendationRepository, times(1)).save(any(Recommendation.class)));
        verify(activityAiService, times(MAX_ATTEMPTS)).generateRecommendation(any(Activity.class));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(messagesWaitingIn(DEAD_LETTER_QUEUE)).isZero());
    }

    @Test
    @DisplayName("a message that is processed successfully is attempted once and never dead-lettered")
    void successfulMessageIsNotDeadLettered() {
        assertDeadLetterQueueExists();
        when(activityAiService.generateRecommendation(any(Activity.class))).thenReturn(someRecommendation());
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(call -> call.getArgument(0));

        publishActivity("act-1");

        await().atMost(PATIENCE).untilAsserted(() ->
                verify(recommendationRepository, times(1)).save(any(Recommendation.class)));
        verify(activityAiService, times(1)).generateRecommendation(any(Activity.class));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(messagesWaitingIn(DEAD_LETTER_QUEUE)).isZero());
    }

    // ---- a message that can never be processed ----

    @Test
    @DisplayName("a message that is not valid JSON is dead-lettered, not lost, without ever reaching the generator")
    void unreadableMessageIsDeadLettered() {
        assertDeadLetterQueueExists();
        Message garbage = MessageBuilder.withBody("this is {not json".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();

        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, garbage);
        Message dead = awaitDeadLetter();

        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("this is {not json");
        verifyNoInteractions(activityAiService);
    }
}
