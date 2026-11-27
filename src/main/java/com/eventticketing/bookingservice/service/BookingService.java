package com.eventticketing.bookingservice.service;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface BookingService {
    BookingResponseDto createBooking(CreateBookingRequestDto request, UUID userId);
    BookingResponseDto getBookingById(UUID id, UUID authenticatedUserId, boolean isAdmin);
    BookingPaginateResponseDto getBookingsByUserId(UUID userId, int page, int size);
    BookingPaginateResponseDto getAllBookings(int page, int size);
    BookingResponseDto cancelBooking(UUID id,UUID userId);
}
