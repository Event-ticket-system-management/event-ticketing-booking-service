package com.eventticketing.bookingservice.service;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import java.util.UUID;

public interface BookingService {
    BookingResponseDto createBooking(CreateBookingRequestDto request, UUID userId);
    BookingResponseDto getBookingById(UUID id);
    BookingPaginateResponseDto getBookingsByUserId(UUID userId, int page, int size);
    BookingPaginateResponseDto getAllBookings(int page, int size);
    BookingResponseDto cancelBooking(UUID id,UUID userId);
}
