package com.eventticketing.bookingservice.dto.response;

import com.eventticketing.bookingservice.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDto {
    private UUID id;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime eventDateTime;
    private Integer totalTickets;
    private Integer availableTickets;
    private BigDecimal ticketPrice;
    private UUID organizerId;
    private EventStatus status;
    private LocalDateTime createdAt;
}