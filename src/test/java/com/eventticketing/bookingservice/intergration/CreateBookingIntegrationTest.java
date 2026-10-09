package com.eventticketing.bookingservice.intergration;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.entity.Booking;
import com.eventticketing.bookingservice.entity.OutboxEvent;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.repository.BookingRepository;
import com.eventticketing.bookingservice.repository.OutboxEventRepository;
import com.eventticketing.bookingservice.service.BookingService;
import com.eventticketing.bookingservice.service.impl.EventResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Create Booking Integration Tests")
class CreateBookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private EventResolver eventResolver;

    private UUID userId;
    private UUID eventId;

    private static final int TICKET_QUANTITY = 2;

    private static final BigDecimal TICKET_PRICE =
            new BigDecimal("2500.00");

    private static final BigDecimal EXPECTED_TOTAL_PRICE =
            new BigDecimal("5000.00");

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        EventResponseDto eventResponse = EventResponseDto.builder()
                .id(eventId)
                .ticketPrice(TICKET_PRICE)
                .availableTickets(50)
                .build();

        when(eventResolver.getValidatedEvent(eventId, TICKET_QUANTITY))
                .thenReturn(eventResponse);
    }

    private CreateBookingRequestDto createRequest(String idempotencyKey) {
        return CreateBookingRequestDto.builder()
                .eventId(eventId)
                .ticketQuantity(TICKET_QUANTITY)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    @Nested
    @DisplayName("Booking Persistence Tests")
    class BookingPersistenceTests {

        @Test
        @DisplayName("Should persist booking with correct details")
        void shouldPersistBookingWithCorrectDetails() {

            CreateBookingRequestDto request =
                    createRequest("booking-persistence-101");

            BookingResponseDto response =
                    bookingService.createBooking(request, userId);

            assertAll(
                    () -> assertNotNull(response),
                    () -> assertNotNull(response.getId()),
                    () -> assertEquals(
                            BookingStatus.PENDING_PAYMENT,
                            response.getStatus()
                    )
            );

            Booking persistedBooking = bookingRepository
                    .findById(response.getId())
                    .orElseThrow();

            assertAll(
                    () -> assertEquals(
                            request.getIdempotencyKey(),
                            persistedBooking.getIdempotencyKey()
                    ),
                    () -> assertEquals(
                            0,
                            EXPECTED_TOTAL_PRICE.compareTo(
                                    persistedBooking.getTotalPrice()
                            )
                    )
            );

            verify(eventResolver, times(1))
                    .getValidatedEvent(eventId, TICKET_QUANTITY);
        }

        @Test
        @DisplayName("Should persist booking and corresponding outbox event")
        void shouldPersistBookingAndOutboxEvent() {

            CreateBookingRequestDto request =
                    createRequest("booking-outbox-102");

            BookingResponseDto response =
                    bookingService.createBooking(request, userId);

            assertNotNull(response);
            assertNotNull(response.getId());

            Booking persistedBooking = bookingRepository
                    .findById(response.getId())
                    .orElseThrow();

            List<OutboxEvent> outboxEvents =
                    outboxEventRepository
                            .findTop10ByProcessedFalseOrderByCreatedAtAsc();

            List<OutboxEvent> matchingEvents = outboxEvents.stream()
                    .filter(event -> persistedBooking.getId()
                            .toString()
                            .equals(event.getAggregateId()))
                    .toList();

            assertEquals(1, matchingEvents.size());

            OutboxEvent persistedOutbox = matchingEvents.getFirst();

            assertAll(
                    () -> assertEquals(
                            "BOOKING",
                            persistedOutbox.getAggregateType()
                    ),
                    () -> assertEquals(
                            "BOOKING_CREATE",
                            persistedOutbox.getEventType()
                    ),
                    () -> assertEquals(
                            Boolean.FALSE,
                            persistedOutbox.isProcessed()
                    )
            );
        }
    }

    @Nested
    @DisplayName("Booking Idempotency Tests")
    class BookingIdempotencyTests {

        @Test
        @DisplayName("Should return existing booking for duplicate idempotency key")
        void shouldReturnExistingBookingForDuplicateRequest() {

            CreateBookingRequestDto request =
                    createRequest("booking-idempotency-201");

            BookingResponseDto firstResponse =
                    bookingService.createBooking(request, userId);

            assertNotNull(firstResponse);
            assertNotNull(firstResponse.getId());

            long bookingCountAfterFirstCall =
                    bookingRepository.count();

            long outboxCountAfterFirstCall =
                    outboxEventRepository.count();

            BookingResponseDto secondResponse =
                    bookingService.createBooking(request, userId);

            assertNotNull(secondResponse);

            assertAll(
                    () -> assertEquals(
                            firstResponse.getId(),
                            secondResponse.getId()
                    ),
                    () -> assertEquals(
                            firstResponse.getStatus(),
                            secondResponse.getStatus()
                    ),
                    () -> assertEquals(
                            bookingCountAfterFirstCall,
                            bookingRepository.count()
                    ),
                    () -> assertEquals(
                            outboxCountAfterFirstCall,
                            outboxEventRepository.count()
                    )
            );

            verify(eventResolver, times(1))
                    .getValidatedEvent(eventId, TICKET_QUANTITY);
        }

        @Test
        @DisplayName("Should create separate bookings for different idempotency keys")
        void shouldCreateBookingsForDifferentIdempotencyKeys() {

            CreateBookingRequestDto firstRequest =
                    createRequest("booking-idempotency-301");

            CreateBookingRequestDto secondRequest =
                    createRequest("booking-idempotency-302");

            BookingResponseDto firstResponse =
                    bookingService.createBooking(firstRequest, userId);

            BookingResponseDto secondResponse =
                    bookingService.createBooking(secondRequest, userId);

            assertNotNull(firstResponse);
            assertNotNull(secondResponse);

            assertNotEquals(
                    firstResponse.getId(),
                    secondResponse.getId()
            );

            assertTrue(
                    bookingRepository.existsById(firstResponse.getId())
            );

            assertTrue(
                    bookingRepository.existsById(secondResponse.getId())
            );

            verify(eventResolver, times(2))
                    .getValidatedEvent(eventId, TICKET_QUANTITY);
        }
    }
}
