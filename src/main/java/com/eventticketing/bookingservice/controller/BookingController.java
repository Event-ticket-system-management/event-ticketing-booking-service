package com.eventticketing.bookingservice.controller;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import com.eventticketing.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody CreateBookingRequestDto request,
            @AuthenticationPrincipal String userId) {

        log.info("REST Request to create booking for user: {}", userId);
        UUID userUuid = extractUserUuid(userId);
        BookingResponseDto response = bookingService.createBooking(request, userUuid);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable UUID id) {
        log.debug("REST Request to get booking details for ID: {}", id);
        BookingResponseDto response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<BookingPaginateResponseDto> getMyBookings(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.debug("REST Request to fetch my bookings for user: {}, page: {}, size: {}", userId, page, size);
        UUID userUuid = extractUserUuid(userId);
        BookingPaginateResponseDto response = bookingService.getBookingsByUserId(userUuid, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingPaginateResponseDto> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.debug("REST Request to fetch all bookings (Admin), page: {}, size: {}", page, size);
        BookingPaginateResponseDto response = bookingService.getAllBookings(page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {

        log.info("REST Request to cancel booking ID: {} by user: {}", id, userId);
        UUID userUuid = extractUserUuid(userId);
        BookingResponseDto response = bookingService.cancelBooking(id, userUuid);
        return ResponseEntity.ok(response);
    }

    private UUID extractUserUuid(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid User UUID string received from Security Context: {}", userId);
            throw new IllegalArgumentException("Invalid user authentication context format");
        }
    }
}