package com.eventticketing.bookingservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventDto {
    private UUID eventId;
    private UUID bookingId;
    private UUID userId;
    private String bookingReference;
    private String paymentId;
    private String eventType;
    private String status;
    private String failureReason;
}