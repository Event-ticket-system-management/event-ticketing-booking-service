package com.eventticketing.bookingservice.scheduler;

import com.eventticketing.bookingservice.entity.OutboxEvent;
import com.eventticketing.bookingservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "booking-events";
    private static final int MAX_RETRIES = 5;
    private static final long KAFKA_TIMEOUT_SECONDS = 3;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> unprocessedEvents = outboxEventRepository.findTop10ByProcessedFalseOrderByCreatedAtAsc();

        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.info("Found {} unprocessed outbox events. Publishing to Kafka topic: {}", unprocessedEvents.size(), TOPIC);

        for (OutboxEvent event : unprocessedEvents) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                        .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);

                log.info("Successfully published outbox event ID: {} to Kafka", event.getId());

            } catch (TimeoutException e) {
                log.error("Kafka publishing TIMEOUT for event ID: {}. Kafka Broker might be down or slow.", event.getId());
                handlePublishFailure(event);
                break;

            } catch (Exception e) {
                log.error("Failed to publish outbox event ID: {}. Cause: {}", event.getId(), e.getMessage());
                handlePublishFailure(event);
            }
        }
    }

    private void handlePublishFailure(OutboxEvent event) {
        event.setRetryCount(event.getRetryCount() + 1);

        if (event.getRetryCount() >= MAX_RETRIES) {
            log.error("Outbox event ID: {} exceeded max retries ({}). Marking as failed for manual review.",
                    event.getId(), MAX_RETRIES);
        }

        outboxEventRepository.save(event);
    }
}