package com.repwise.aiservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the AI service, the consuming side of the activity events.
 *
 * <p>Declares the same queue, exchange and binding as the activity service, using the same
 * names from {@code application.properties}, so the topology exists no matter which service
 * starts first. Declaring identical settings twice is harmless, but a queue declared with
 * different settings is refused by the broker ({@code PRECONDITION_FAILED}), so the two services
 * must always agree exactly.
 *
 * <p>Failed messages are not lost and not retried forever: the activity queue dead-letters them
 * to a dead-letter exchange, which routes them to a dead-letter queue. The listener's retries
 * before that happen are configured in {@code application.properties}
 * ({@code spring.rabbitmq.listener.simple.retry.*}).
 *
 * <pre>
 *   activity exchange --&gt; activity queue --(rejected, retries used up)--&gt;
 *   dead-letter exchange --&gt; dead-letter queue
 * </pre>
 *
 * <p>The JSON {@link MessageConverter} is applied by Spring Boot to {@code @RabbitListener}
 * containers, which turns incoming JSON bodies into Java objects.
 */
@Configuration
public class RabbitMqConfig {

    @Value("${rabbitmq.queue.name}")
    private String queue;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    /** Name of the dead-letter queue, where messages that could not be processed are parked. */
    @Value("${rabbitmq.dlq.name}")
    private String dlQueue;

    /** Name of the dead-letter exchange, which the activity queue hands failed messages to. */
    @Value("${rabbitmq.dlx.name}")
    private String dlExchange;

    /** Routing key under which the dead-letter queue is bound to the dead-letter exchange. */
    @Value("${rabbitmq.dlq.routing.key}")
    private String dlRoutingKey;

    /**
     * Declares the durable queue that activity events are read from.
     *
     * <p>A message that is rejected without being requeued, for example when the listener has used
     * up its retries or the body cannot be read, is handed to the dead-letter exchange with the
     * dead-letter routing key. These arguments are fixed when the queue is first created: changing
     * them later means deleting the queue on the broker first.
     *
     * @return the durable configured queue with its dead-letter settings
     */
    @Bean
    public Queue activityQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(dlExchange)
                .deadLetterRoutingKey(dlRoutingKey)
                .build();
    }

    /**
     * Declares the dead-letter queue, where messages that could not be processed are parked.
     *
     * <p>It is durable, so parked messages survive a broker restart. It deliberately has no
     * dead-letter settings of its own, so a message can never bounce between queues forever.
     * Nothing consumes it: messages wait here until someone inspects or replays them.
     *
     * @return the durable dead-letter queue
     */
    @Bean
    public Queue dlq() {
        return new Queue(dlQueue);
    }

    /**
     * Declares the durable direct exchange that the activity service publishes to.
     *
     * @return the durable configured direct exchange
     */
    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange(exchange, true, false);
    }

    /**
     * Declares the dead-letter exchange that the activity queue sends rejected messages to.
     *
     * @return the durable direct dead-letter exchange
     */
    @Bean
    public DirectExchange dlx() { return new DirectExchange(dlExchange, true, false); }

    /**
     * Binds the activity queue to the activity exchange under the configured routing key.
     *
     * @param activityQueue the queue to bind
     * @param activityExchange the exchange to bind it to
     * @return the binding between the two
     */
    @Bean
    public Binding activityBinding(Queue activityQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityQueue).to(activityExchange).with(routingKey);
    }

    /**
     * Binds the dead-letter queue to the dead-letter exchange under the dead-letter routing key,
     * so a message dead-lettered from the activity queue ends up in the dead-letter queue.
     *
     * <p>The parameter names {@code dlq} and {@code dlx} must match the bean method names above:
     * with two queues and two exchanges declared, Spring uses the parameter name to decide which
     * one to inject.
     *
     * @param dlq the dead-letter queue
     * @param dlx the dead-letter exchange
     * @return the binding between the two
     */
    @Bean
    public Binding dlActivityBinding(Queue dlq, DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(dlRoutingKey);
    }

    /**
     * Provides the converter that deserializes incoming JSON message bodies.
     *
     * @return a Jackson-based JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
