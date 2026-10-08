package com.eventticketing.bookingservice.mapper;

import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class ObjectMapper {

    public BookingResponseDto toBookingResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        return BookingResponseDto.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .idempotencyKey(booking.getIdempotencyKey())
                .userId(booking.getUserId())
                .eventId(booking.getEventId())
                .ticketQuantity(booking.getTicketQuantity())
                .totalPrice(booking.getTotalPrice())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

}