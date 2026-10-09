package com.eventticketing.bookingservice.intergration;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.exception.ResourceNotFoundException;
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
@DisplayName("Get Booking Integration Tests")
class GetBookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @MockitoBean
    private EventResolver eventResolver;

    private UUID userId;
    private UUID eventId;

    private static final int TICKET_QUANTITY = 2;
    private static final BigDecimal TICKET_PRICE =
            new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        EventResponseDto eventResponse =
                EventResponseDto.builder()
                        .id(eventId)
                        .ticketPrice(TICKET_PRICE)
                        .availableTickets(100)
                        .build();

        when(eventResolver.getValidatedEvent(
                eventId, TICKET_QUANTITY
        )).thenReturn(eventResponse);
    }

    private BookingResponseDto createBooking(
            UUID ownerId,
            String idempotencyKey
    ) {
        CreateBookingRequestDto request =
                CreateBookingRequestDto.builder()
                        .eventId(eventId)
                        .ticketQuantity(TICKET_QUANTITY)
                        .idempotencyKey(idempotencyKey)
                        .build();

        return bookingService.createBooking(request, ownerId);
    }

    @Test
    @DisplayName("Should retrieve booking details by ID")
    void shouldRetrieveBookingById() {

        String key = UUID.randomUUID().toString();

        BookingResponseDto createdBooking =
                createBooking(userId, key);

        BookingResponseDto fetchedBooking =
                bookingService.getBookingById(
                        createdBooking.getId()
                );

        assertNotNull(fetchedBooking);

        assertAll(
                () -> assertEquals(
                        createdBooking.getId(),
                        fetchedBooking.getId()
                ),
                () -> assertEquals(
                        key,
                        fetchedBooking.getIdempotencyKey()
                ),
                () -> assertEquals(
                        BookingStatus.PENDING_PAYMENT,
                        fetchedBooking.getStatus()
                )
        );
    }

    @Test
    @DisplayName("Should reject nonexistent booking ID")
    void shouldThrowWhenBookingNotFound() {

        UUID nonExistentId = UUID.randomUUID();

        assertThrows(
                ResourceNotFoundException.class,
                () -> bookingService.getBookingById(
                        nonExistentId
                )
        );
    }

    @Test
    @DisplayName("Should return only bookings belonging to requested user")
    void shouldRetrieveOnlyUserBookings() {

        UUID anotherUserId = UUID.randomUUID();

        BookingResponseDto firstBooking =
                createBooking(
                        userId,
                        UUID.randomUUID().toString()
                );

        BookingResponseDto secondBooking =
                createBooking(
                        userId,
                        UUID.randomUUID().toString()
                );

        BookingResponseDto otherUserBooking =
                createBooking(
                        anotherUserId,
                        UUID.randomUUID().toString()
                );

        BookingPaginateResponseDto response =
                bookingService.getBookingsByUserId(
                        userId, 0, 10
                );

        assertNotNull(response);
        assertNotNull(response.getDataList());

        var returnedIds = response.getDataList()
                .stream()
                .map(BookingResponseDto::getId)
                .toList();

        assertAll(
                () -> assertTrue(
                        returnedIds.contains(firstBooking.getId())
                ),
                () -> assertTrue(
                        returnedIds.contains(secondBooking.getId())
                ),
                () -> assertFalse(
                        returnedIds.contains(otherUserBooking.getId())
                ),
                () -> assertEquals(2, returnedIds.size())
        );
    }

    @Test
    @DisplayName("Should return empty list for user without bookings")
    void shouldReturnEmptyListForUserWithoutBookings() {

        UUID unknownUserId = UUID.randomUUID();

        BookingPaginateResponseDto response =
                bookingService.getBookingsByUserId(
                        unknownUserId, 0, 10
                );

        assertNotNull(response);
        assertNotNull(response.getDataList());

        assertTrue(response.getDataList().isEmpty());
        assertEquals(0, response.getDataCount());
    }
}
