package com.eventticketing.bookingservice.service.impl;

import com.eventticketing.bookingservice.dto.event.PaymentEventDto;
import com.eventticketing.bookingservice.entity.Booking;
import com.eventticketing.bookingservice.entity.ProcessedPaymentEvent;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.exception.InvalidBookingStateException;
import com.eventticketing.bookingservice.exception.ResourceNotFoundException;
import com.eventticketing.bookingservice.repository.BookingRepository;
import com.eventticketing.bookingservice.repository.ProcessedPaymentEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPaymentEventHandler {

    private final ProcessedPaymentEventRepository processedEventRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public void handlePaymentCompleted(PaymentEventDto event){
        if (processedEventRepository.existsById(event.getEventId())){
            return;
        }

        Booking selectedBooking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found " + event.getBookingId()));



        if (selectedBooking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException(
                    "Invalid booking status: " + selectedBooking.getStatus()
            );
        }

        if (!selectedBooking.getUserId().equals(event.getUserId())){
            throw new IllegalStateException("Payment user mismatch");
        }

        selectedBooking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(selectedBooking);

        processedEventRepository.save(
                new ProcessedPaymentEvent(event.getEventId())
        );

    }


    @Transactional
    public void handlePaymentFailed(PaymentEventDto event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            return;
        }

        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking not found: " + event.getBookingId()
                ));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Invalid booking status: " + booking.getStatus()
            );
        }

        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);

        processedEventRepository.save(
                new ProcessedPaymentEvent(event.getEventId())
        );
    }

}
