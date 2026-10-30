package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumes activity events published by the activity service.
 *
 * <p>The queue name comes from {@code rabbitmq.queue.name}. Incoming JSON bodies are converted
 * to {@link Activity} using the parameter type of the listener method, not the producer's class
 * name. Each activity is passed to {@link ActivityAiService} to generate a recommendation, which
 * is currently only logged, not saved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAiService activityAiService;

    /**
     * Handles one activity event from the queue.
     *
     * <p>The message is acknowledged when this method returns normally. If it throws, Spring
     * rejects the message and, by default, requeues it. That includes a failed Gemini call or
     * an unparseable reply, so the message is retried until it succeeds.
     *
     * @param activity the activity that was recorded
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void processActivity(Activity activity) {
        log.info("Received activity for processing: {}", activity.getId());

        Recommendation recommendation = activityAiService.generateRecommendation(activity);

        log.info("Generated recommendation for activity {}: {}", activity.getId(), recommendation);
    }
}
