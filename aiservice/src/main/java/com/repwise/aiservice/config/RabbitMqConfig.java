package com.repwise.aiservice.config;

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
 * RabbitMQ configuration for the AI service, the consuming side of the activity events.
 *
 * <p>Declares the same queue, exchange and binding as the activity service, using the same
 * names from {@code application.properties}, so the topology exists no matter which service
 * starts first. Declaring identical settings twice is harmless. The JSON {@link MessageConverter}
 * is applied by Spring Boot to {@code @RabbitListener} containers, which turns incoming JSON
 * bodies into Java objects.
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
     * Declares the durable queue that activity events are read from.
     *
     * @return the durable configured queue
     */
    @Bean
    public Queue activityQueue() {
        return new Queue(queue, true);
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
     * Provides the converter that deserializes incoming JSON message bodies.
     *
     * @return a Jackson-based JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
