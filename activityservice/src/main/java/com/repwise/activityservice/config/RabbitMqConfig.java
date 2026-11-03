package com.repwise.activityservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the activity service.
 *
 * <p>Declares the queue that activity events are published to, the dead-letter exchange and queue
 * where messages the consumer could not process end up, and the converter that turns Java objects
 * into JSON message bodies. The AI service declares the same queue, and the broker refuses a
 * second declaration with different settings ({@code PRECONDITION_FAILED}), so the two services
 * must always agree exactly. Connection details (host, port, credentials) are not
 * set here; Spring Boot's AMQP auto-configuration reads them from {@code spring.rabbitmq.*}
 * properties and defaults to {@code localhost:5672} with {@code guest/guest}.
 *
 * <p>Both beans are picked up automatically by Spring Boot: the {@link Queue} is declared on
 * the broker through the auto-configured {@code RabbitAdmin} when the first connection is
 * opened, and the {@link MessageConverter} is applied to the auto-configured
 * {@code RabbitTemplate} and to {@code @RabbitListener} containers.
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
     * Declares the queue that carries activity events to their consumers.
     *
     * <p>The queue is durable, so the queue definition survives a broker restart. Declaring it
     * is idempotent: if it already exists with the same settings nothing happens, but if it
     * exists with different settings the broker rejects the declaration. Messages sent to it
     * also need to be marked persistent (the default for {@code RabbitTemplate}) to survive a
     * restart.
     *
     * <p>Messages that the consumer rejects without requeueing (retries used up, or an unreadable
     * body) are handed to the dead-letter exchange with the dead-letter routing key instead of
     * being dropped. These arguments are fixed when the queue is first created: changing them
     * later means deleting the queue on the broker first.
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
     * Provides the converter used to serialize outgoing messages to JSON and deserialize
     * incoming ones, instead of Java serialization.
     *
     * <p>JSON keeps the message readable in the RabbitMQ management UI and lets consumers in
     * other services read it without sharing the producer's classes.
     *
     * @return a Jackson-based JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declares the exchange that producers publish activity events to.
     *
     * <p>A direct exchange delivers a message to the queues whose binding key exactly equals
     * the message's routing key. It is durable, like the queue.
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
     * <p>Without a binding the exchange would have nowhere to send messages and would silently
     * drop them.
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
}
