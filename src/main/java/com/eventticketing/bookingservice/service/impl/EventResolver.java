package com.eventticketing.bookingservice.service.impl;

import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.exception.InsufficientTicketsException;
import com.eventticketing.bookingservice.exception.ResourceNotFoundException;
import com.eventticketing.bookingservice.service.client.EventServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventResolver {

    private final StringRedisTemplate redisTemplate;
    private final EventServiceClient eventServiceClient;
    private final ObjectMapper objectMapper;

    private static final String REDIS_EVENT_KEY_PREFIX = "event::";

    public EventResponseDto getValidatedEvent(UUID eventId, Integer requestedTickets) {
        String cacheKey = REDIS_EVENT_KEY_PREFIX + eventId.toString();
        EventResponseDto eventDto = null;

        try {
            String cachedEventJson = redisTemplate.opsForValue().get(cacheKey);

            if (cachedEventJson != null) {
                log.info("Cache HIT in Booking Service for Event ID: {}", eventId);
                eventDto = objectMapper.readValue(cachedEventJson, EventResponseDto.class);
            }
        } catch (Exception e) {
            log.error("Redis read failed for Event ID: {}. Falling back to REST client. Error: {}", eventId, e.getMessage());
        }

        if (eventDto == null) {
            log.warn("Cache MISS for Event ID: {}. Fetching from Event Service via Feign REST.", eventId);
            try {
                eventDto = eventServiceClient.getEventById(eventId);
            } catch (Exception e) {
                log.error("Feign REST call failed for Event ID: {}. Error: {}", eventId, e.getMessage());
                throw new ResourceNotFoundException(String.format("Event not found with ID: %s",
                        eventId));
            }
        }

        if (eventDto == null) {
            throw new ResourceNotFoundException(String.format("Event not found with ID: %s",
                    eventId));
        }

        if (eventDto.getAvailableTickets() < requestedTickets) {
            throw new InsufficientTicketsException(
                    String.format("Not enough tickets available. Requested: %d, Available: %d",
                            requestedTickets, eventDto.getAvailableTickets())
            );
        }

        return eventDto;
    }
}