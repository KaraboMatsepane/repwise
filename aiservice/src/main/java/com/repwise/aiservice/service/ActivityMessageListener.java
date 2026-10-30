package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumes activity events published by the activity service.
 *
 * <p>The queue name comes from {@code rabbitmq.queue.name}. Incoming JSON bodies are converted
 * to {@link Activity} using the parameter type of the listener method, not the producer's class
 * name. This class currently only logs the activity id; recommendation generation is not
 * implemented yet.
 */
@Slf4j
@Service
public class ActivityMessageListener {

    /**
     * Handles one activity event from the queue.
     *
     * <p>The message is acknowledged when this method returns normally. If it throws, Spring
     * rejects the message and, by default, requeues it.
     *
     * @param activity the activity that was recorded
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void processActivity(Activity activity) {
        log.info("Received activity for processing: {}", activity.getId());
    }
}
