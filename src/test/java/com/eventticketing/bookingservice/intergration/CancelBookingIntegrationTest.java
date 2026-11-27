package com.eventticketing.bookingservice.intergration;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.entity.Booking;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.exception.InvalidBookingStateException;
import com.eventticketing.bookingservice.exception.UnauthorizedAccessException;
import com.eventticketing.bookingservice.repository.BookingRepository;
import com.eventticketing.bookingservice.service.BookingService;
import com.eventticketing.bookingservice.service.impl.EventResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Cancel Booking Integration Tests")
class CancelBookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private EventResolver eventResolver;

    private UUID userId;
    private UUID eventId;

    private static final int TICKET_QUANTITY = 2;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        EventResponseDto eventResponse = EventResponseDto.builder()
                .id(eventId)
                .ticketPrice(new BigDecimal("1500.00"))
                .availableTickets(30)
                .build();

        when(eventResolver.getValidatedEvent(
                eventId,
                TICKET_QUANTITY
        )).thenReturn(eventResponse);
    }

    private BookingResponseDto createBooking(String idempotencyKey) {

        CreateBookingRequestDto request =
                CreateBookingRequestDto.builder()
                        .eventId(eventId)
                        .ticketQuantity(TICKET_QUANTITY)
                        .idempotencyKey(idempotencyKey)
                        .build();

        return bookingService.createBooking(request, userId);
    }

    @Test
    @DisplayName("Should cancel booking successfully when requested by owner")
    void shouldCancelBookingSuccessfully() {

        BookingResponseDto createdBooking =
                createBooking("cancel-success-303");

        BookingResponseDto cancelledResponse =
                bookingService.cancelBooking(
                        createdBooking.getId(),
                        userId
                );

        assertNotNull(cancelledResponse);

        assertEquals(
                BookingStatus.CANCELLED,
                cancelledResponse.getStatus()
        );

        Booking persistedBooking = bookingRepository
                .findById(createdBooking.getId())
                .orElseThrow();

        assertEquals(
                BookingStatus.CANCELLED,
                persistedBooking.getStatus()
        );
    }

    @Test
    @DisplayName("Should reject cancellation by unauthorized user")
    void shouldRejectUnauthorizedCancellation() {

        BookingResponseDto createdBooking =
                createBooking("cancel-security-404");

        UUID unauthorizedUserId = UUID.randomUUID();

        assertThrows(
                UnauthorizedAccessException.class,
                () -> bookingService.cancelBooking(
                        createdBooking.getId(),
                        unauthorizedUserId
                )
        );

        Booking persistedBooking = bookingRepository
                .findById(createdBooking.getId())
                .orElseThrow();

        assertEquals(
                BookingStatus.PENDING_PAYMENT,
                persistedBooking.getStatus()
        );
    }

    @Test
    @DisplayName("Should reject cancellation of already cancelled booking")
    void shouldRejectAlreadyCancelledBooking() {

        BookingResponseDto createdBooking =
                createBooking("cancel-duplicate-505");

        bookingService.cancelBooking(
                createdBooking.getId(),
                userId
        );

        assertThrows(
                InvalidBookingStateException.class,
                () -> bookingService.cancelBooking(
                        createdBooking.getId(),
                        userId
                )
        );

        Booking persistedBooking = bookingRepository
                .findById(createdBooking.getId())
                .orElseThrow();

        assertEquals(
                BookingStatus.CANCELLED,
                persistedBooking.getStatus()
        );
    }
}