package com.repwise.aiservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Specifies the RabbitMQ topology the AI service declares: the activity queue dead-letters failed
 * messages to a dead-letter exchange, which routes them to a dead-letter queue.
 *
 * <p>Both the activity service and the AI service declare the same queue, and RabbitMQ refuses a
 * second declaration whose arguments differ from the first. This test exists in both services
 * with identical expectations so they cannot drift apart.
 *
 * <p>No broker is needed: the configuration class is loaded into a throwaway Spring context and
 * the declared queues, exchanges and bindings are inspected. The names are deliberately unusual,
 * to prove they are read from configuration and not hard-coded.
 *
 * <pre>
 *   activity exchange --(routing key)--> activity queue --(rejected / retries exhausted)-->
 *   dead-letter exchange --(dead-letter routing key)--> dead-letter queue
 * </pre>
 */
class RabbitMqTopologyTest {

    private static final String QUEUE = "test.activity.queue";
    private static final String EXCHANGE = "test.activity.exchange";
    private static final String ROUTING_KEY = "test.activity.key";
    private static final String DEAD_LETTER_EXCHANGE = "test.dead.exchange";
    private static final String DEAD_LETTER_QUEUE = "test.dead.queue";
    private static final String DEAD_LETTER_ROUTING_KEY = "test.dead.key";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RabbitMqConfig.class)
            .withPropertyValues(
                    "rabbitmq.queue.name=" + QUEUE,
                    "rabbitmq.exchange.name=" + EXCHANGE,
                    "rabbitmq.routing.key=" + ROUTING_KEY,
                    "rabbitmq.dlx.name=" + DEAD_LETTER_EXCHANGE,
                    "rabbitmq.dlq.name=" + DEAD_LETTER_QUEUE,
                    "rabbitmq.dlq.routing.key=" + DEAD_LETTER_ROUTING_KEY);

    private void inContext(Consumer<AssertableApplicationContext> assertions) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertions.accept(context);
        });
    }

    private Queue queueNamed(AssertableApplicationContext context, String name) {
        List<Queue> declared = List.copyOf(context.getBeansOfType(Queue.class).values());
        return declared.stream().filter(queue -> queue.getName().equals(name)).findFirst()
                .orElseGet(() -> fail("Expected a queue named '%s' to be declared, but the declared queues are %s",
                        name, declared.stream().map(Queue::getName).toList()));
    }

    private Exchange exchangeNamed(AssertableApplicationContext context, String name) {
        List<Exchange> declared = List.copyOf(context.getBeansOfType(Exchange.class).values());
        return declared.stream().filter(exchange -> exchange.getName().equals(name)).findFirst()
                .orElseGet(() -> fail("Expected an exchange named '%s' to be declared, but the declared exchanges are %s",
                        name, declared.stream().map(Exchange::getName).toList()));
    }

    private Binding bindingFrom(AssertableApplicationContext context, String exchange, String queue) {
        List<Binding> declared = List.copyOf(context.getBeansOfType(Binding.class).values());
        return declared.stream()
                .filter(binding -> binding.getExchange().equals(exchange) && binding.getDestination().equals(queue))
                .findFirst()
                .orElseGet(() -> fail("Expected queue '%s' to be bound to exchange '%s', but the declared bindings are %s",
                        queue, exchange,
                        declared.stream().map(b -> b.getExchange() + " -> " + b.getDestination()).toList()));
    }

    // ---- the activity queue and its dead-letter settings ----

    @Test
    @DisplayName("the activity queue sends failed messages to the dead-letter exchange")
    void activityQueueDeadLettersToTheDeadLetterExchange() {
        inContext(context -> assertThat(queueNamed(context, QUEUE).getArguments())
                .containsEntry("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE));
    }

    @Test
    @DisplayName("the activity queue sends failed messages with the dead-letter routing key")
    void activityQueueDeadLettersWithTheDeadLetterRoutingKey() {
        inContext(context -> assertThat(queueNamed(context, QUEUE).getArguments())
                .containsEntry("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY));
    }

    @Test
    @DisplayName("the activity queue is still durable")
    void activityQueueIsStillDurable() {
        inContext(context -> assertThat(queueNamed(context, QUEUE).isDurable()).isTrue());
    }

    @Test
    @DisplayName("the activity queue is still bound to the activity exchange under the activity routing key")
    void activityQueueIsStillBoundToTheActivityExchange() {
        inContext(context -> assertThat(bindingFrom(context, EXCHANGE, QUEUE).getRoutingKey())
                .isEqualTo(ROUTING_KEY));
    }

    // ---- the dead-letter exchange and queue ----

    @Test
    @DisplayName("a dead-letter queue is declared, and it is durable so failed messages survive a restart")
    void deadLetterQueueIsDeclaredAndDurable() {
        inContext(context -> assertThat(queueNamed(context, DEAD_LETTER_QUEUE).isDurable()).isTrue());
    }

    @Test
    @DisplayName("the dead-letter queue does not dead-letter again, so a failed message can never loop")
    void deadLetterQueueDoesNotDeadLetterAgain() {
        inContext(context -> assertThat(queueNamed(context, DEAD_LETTER_QUEUE).getArguments())
                .doesNotContainKey("x-dead-letter-exchange")
                .doesNotContainKey("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("the dead-letter exchange is a durable direct exchange")
    void deadLetterExchangeIsADurableDirectExchange() {
        inContext(context -> {
            Exchange exchange = exchangeNamed(context, DEAD_LETTER_EXCHANGE);
            assertThat(exchange).isInstanceOf(DirectExchange.class);
            assertThat(exchange.isDurable()).isTrue();
        });
    }

    @Test
    @DisplayName("the dead-letter queue is bound to the dead-letter exchange under the dead-letter routing key")
    void deadLetterQueueIsBoundToTheDeadLetterExchange() {
        inContext(context -> assertThat(bindingFrom(context, DEAD_LETTER_EXCHANGE, DEAD_LETTER_QUEUE).getRoutingKey())
                .isEqualTo(DEAD_LETTER_ROUTING_KEY));
    }
}
