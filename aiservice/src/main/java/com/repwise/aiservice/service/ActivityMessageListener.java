package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
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
 * is then saved through {@link RecommendationRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAiService activityAiService;
    private final RecommendationRepository recommendationRepository;

    /**
     * Handles one activity event from the queue.
     *
     * <p>The message is acknowledged when this method returns normally. If it throws, Spring
     * rejects the message and, by default, requeues it. That includes a failed Gemini call or
     * an unparseable reply or a failed database save, so the message is retried until it succeeds.
     *
     * @param activity the activity that was recorded
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void processActivity(Activity activity) {
        log.info("Received activity for processing: {}", activity.getId());

        Recommendation recommendation = activityAiService.generateRecommendation(activity);

        Recommendation saved = recommendationRepository.save(recommendation);

        log.info("Saved recommendation {} for activity {}: {}", saved.getId(), activity.getId(), saved);
    }
}
