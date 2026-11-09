package com.eventticketing.bookingservice.consumer;

import com.eventticketing.bookingservice.dto.event.PaymentEventDto;
import com.eventticketing.bookingservice.service.impl.BookingPaymentEventHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final BookingPaymentEventHandler handler;
    private final ObjectMapper objectMapper;


    @PostConstruct
    public void init() {
        log.info("PaymentEventConsumer bean initialized successfully");
    }

    @KafkaListener(
        topics = "payment.completed",
        groupId = "booking-service-group"
    )
    public void consumePaymentEvent(String messagePayload) {
        log.info("Received payment event from Kafka: {}", messagePayload);

            PaymentEventDto paymentEvent = objectMapper.readValue(messagePayload, PaymentEventDto.class);

            if (!"payment.completed".equals(paymentEvent.getEventType())
                    || !"SUCCESS".equals(paymentEvent.getStatus())) {
                throw new IllegalArgumentException(
                        "Invalid payment.completed event"
                );
            }


            handler.handlePaymentCompleted(paymentEvent);

        log.info(
                "Payment completed for booking: {}",
                paymentEvent.getBookingId()
        );

    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "booking-service-group"
    )
    public void consumePaymentFailed(String message){

        PaymentEventDto paymentEvent = objectMapper.readValue(
                message,
                PaymentEventDto.class
        );

        if (!"payment.failed".equals(paymentEvent.getEventType())
                || !"FAILED".equals(paymentEvent.getStatus())) {
            throw new IllegalArgumentException(
                    "Invalid payment.failed event"
            );
        }

        handler.handlePaymentFailed(paymentEvent);

        log.info(
                "Payment failed for booking: {}",
                paymentEvent.getBookingId()
        );
    }
}