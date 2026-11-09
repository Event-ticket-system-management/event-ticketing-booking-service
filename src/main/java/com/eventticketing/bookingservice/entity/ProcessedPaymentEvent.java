package com.eventticketing.bookingservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_events")
public class ProcessedPaymentEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedPaymentEvent() {}

    public ProcessedPaymentEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}