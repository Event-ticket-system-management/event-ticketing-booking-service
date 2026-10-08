package com.eventticketing.bookingservice.service.client;

import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "event-service", url = "${EVENT_SERVICE_URL}")
public interface EventServiceClient {
    @GetMapping("/api/v1/events/{eventId}")
    EventResponseDto getEventById(@PathVariable("eventId") UUID eventId);
}
