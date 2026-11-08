package com.repwise.aiservice.service;

import com.repwise.aiservice.model.Activity;
import com.repwise.aiservice.model.Recommendation;
import com.repwise.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Consumes activity events published by the activity service.
 *
 * <p>The queue name comes from {@code rabbitmq.queue.name}. Incoming JSON bodies are converted
 * to {@link Activity} using the parameter type of the listener method, not the producer's class
 * name. Each activity is passed to {@link ActivityAiService} to generate a recommendation, which
 * is then saved through {@link RecommendationRepository}.
 *
 * <p>Failures are not retried forever and not lost. Every failure must reach the message
 * container as an exception, because it reacts by retrying the delivery up to three times in
 * total (the {@code spring.rabbitmq.listener.simple.retry.*} settings in
 * {@code application.properties}) and then rejecting the message without requeueing it. The
 * activity queue then dead-letters it to the dead-letter queue, where it waits to be inspected.
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
     * <p>The message is acknowledged when this method returns normally. If it throws, which
     * includes a failed Gemini call, a reply that is not the expected JSON and a failed database
     * save, the delivery is retried after a short pause. After the third attempt the message is
     * rejected without being requeued and moves to the dead-letter queue. A message whose body
     * cannot be converted to an {@link Activity} never reaches this method and goes to the
     * dead-letter queue straight away.
     *
     * <p>An activity has at most one recommendation. RabbitMQ can deliver the same message more
     * than once, so an activity that already has a recommendation is skipped without calling
     * Gemini, and the message is acknowledged: the work is already done. If two copies are
     * processed at the same moment, both can pass that check; the unique index on
     * {@code activityId} then refuses the second save, and that copy is acknowledged too.
     *
     * <p>Do not catch other exceptions here: a swallowed failure would acknowledge the message and
     * lose it silently.
     *
     * @param activity the activity that was recorded
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void processActivity(Activity activity) {
        log.info("Received activity for processing: {}", activity.getId());

        if (recommendationRepository.existsByActivityId(activity.getId())) {
            log.info("Activity {} already has a recommendation, skipping the redelivered message",
                    activity.getId());
            return;
        }

        Recommendation recommendation = activityAiService.generateRecommendation(activity);

        Recommendation saved;
        try {
            saved = recommendationRepository.save(recommendation);
        } catch (DuplicateKeyException e) {
            log.info("Activity {} got a recommendation from a parallel delivery, discarding this one",
                    activity.getId());
            return;
        }

        log.info("Saved recommendation {} for activity {}: {}", saved.getId(), activity.getId(), saved);
    }
}
