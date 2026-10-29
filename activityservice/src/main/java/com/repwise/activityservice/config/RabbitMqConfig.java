package com.repwise.activityservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the activity service.
 *
 * <p>Declares the queue that activity events are published to and the converter that turns
 * Java objects into JSON message bodies. Connection details (host, port, credentials) are not
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

    /**
     * Declares the queue that carries activity events to their consumers.
     *
     * <p>The queue is durable, so the queue definition survives a broker restart. Declaring it
     * is idempotent: if it already exists with the same settings nothing happens, but if it
     * exists with different settings the broker rejects the declaration. Messages sent to it
     * also need to be marked persistent (the default for {@code RabbitTemplate}) to survive a
     * restart.
     *
     * @return the durable configured queue
     */
    @Bean
    public Queue activityQueue() {
        return new Queue(queue, true);
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
}
