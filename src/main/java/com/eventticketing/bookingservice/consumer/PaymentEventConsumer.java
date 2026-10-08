package com.eventticketing.bookingservice.consumer;

import com.eventticketing.bookingservice.dto.event.PaymentCompletedEvent;
import com.eventticketing.bookingservice.entity.Booking;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.exception.EventProcessingException;
import com.eventticketing.bookingservice.exception.ResourceNotFoundException;
import com.eventticketing.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "payment-events", 
        groupId = "booking-service-group"
    )
    @Transactional
    public void consumePaymentEvent(String messagePayload) {
        log.info("Received payment event from Kafka: {}", messagePayload);

        try {
            PaymentCompletedEvent paymentEvent = objectMapper.readValue(messagePayload, PaymentCompletedEvent.class);
            
            Booking booking = bookingRepository.findById(paymentEvent.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException(String.format("Booking not found with ID: %s ", paymentEvent.getBookingId())));

            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                log.warn("Booking ID: {} is already in status: {}. Ignoring duplicate payment event.", 
                        booking.getId(), booking.getStatus());
                return;
            }

            if ("SUCCESS".equalsIgnoreCase(paymentEvent.getStatus())) {
                booking.setStatus(BookingStatus.CONFIRMED);
                log.info("Booking ID: {} successfully CONFIRMED after payment success.", booking.getId());
            } else {
                booking.setStatus(BookingStatus.CANCELLED);
                log.info("Booking ID: {} CANCELLED due to payment failure. Reason: {}", 
                        booking.getId(), paymentEvent.getFailureReason());
            }

            bookingRepository.save(booking);

        } catch (Exception e) {
            log.error("Error processing payment event payload: {}", messagePayload, e);
            throw new EventProcessingException("Failed to process payment event");
        }
    }
}