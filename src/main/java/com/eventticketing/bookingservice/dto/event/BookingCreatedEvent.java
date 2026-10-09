package com.eventticketing.bookingservice.dto.event;

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
public class BookingCreatedEvent {
    private UUID bookingId;
    private String bookingReference;
    private String idempotencyKey;
    private UUID userId;
    private UUID eventId;
    private Integer ticketQuantity;
    private BigDecimal totalPrice;
    private String currency;
    private LocalDateTime createdAt;
}