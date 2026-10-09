
package com.eventticketing.bookingservice.service;

import com.eventticketing.bookingservice.dto.event.BookingCreatedEvent;
import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import com.eventticketing.bookingservice.entity.Booking;
import com.eventticketing.bookingservice.entity.OutboxEvent;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.exception.EventSerializationException;
import com.eventticketing.bookingservice.exception.InvalidBookingStateException;
import com.eventticketing.bookingservice.exception.ResourceNotFoundException;
import com.eventticketing.bookingservice.exception.UnauthorizedAccessException;
import com.eventticketing.bookingservice.mapper.ObjectMapper;
import com.eventticketing.bookingservice.repository.BookingRepository;
import com.eventticketing.bookingservice.repository.OutboxEventRepository;
import com.eventticketing.bookingservice.service.impl.BookingServiceImpl;
import com.eventticketing.bookingservice.service.impl.EventResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingServiceImpl Unit Tests")
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EventResolver eventResolver;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private tools.jackson.databind.ObjectMapper jsonObjectMapper;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private UUID userId;
    private UUID eventId;
    private UUID bookingId;

    private CreateBookingRequestDto request;
    private Booking booking;
    private BookingResponseDto response;
    private EventResponseDto eventResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        request = CreateBookingRequestDto.builder()
                .eventId(eventId)
                .ticketQuantity(2)
                .idempotencyKey("idem-key-123")
                .build();

        eventResponse = EventResponseDto.builder()
                .id(eventId)
                .ticketPrice(new BigDecimal("1500.00"))
                .availableTickets(100)
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .bookingReference("BK-20261009-8A2F")
                .idempotencyKey("idem-key-123")
                .userId(userId)
                .eventId(eventId)
                .ticketQuantity(2)
                .totalPrice(new BigDecimal("3000.00"))
                .status(BookingStatus.PENDING_PAYMENT)
                .createdAt(LocalDateTime.now())
                .build();

        response = BookingResponseDto.builder()
                .id(bookingId)
                .bookingReference(booking.getBookingReference())
                .idempotencyKey(booking.getIdempotencyKey())
                .userId(userId)
                .eventId(eventId)
                .ticketQuantity(2)
                .totalPrice(new BigDecimal("3000.00"))
                .status(BookingStatus.PENDING_PAYMENT)
                .build();
    }

    @Nested
    @DisplayName("Create Booking Tests")
    class CreateBookingTests {

        @Test
        @DisplayName("Should create booking and outbox event")
        void shouldCreateBookingSuccessfully() {

            when(bookingRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());

            when(eventResolver.getValidatedEvent(eventId, 2))
                    .thenReturn(eventResponse);

            when(bookingRepository.save(any(Booking.class)))
                    .thenReturn(booking);

            when(jsonObjectMapper.writeValueAsString(
                    any(BookingCreatedEvent.class)))
                    .thenReturn("{\"bookingId\":\"test\"}");

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(response);

            BookingResponseDto result =
                    bookingService.createBooking(request, userId);

            assertNotNull(result);
            assertEquals(bookingId, result.getId());
            assertEquals(
                    BookingStatus.PENDING_PAYMENT,
                    result.getStatus()
            );
            assertEquals(
                    new BigDecimal("3000.00"),
                    result.getTotalPrice()
            );

            ArgumentCaptor<Booking> bookingCaptor =
                    ArgumentCaptor.forClass(Booking.class);

            verify(bookingRepository)
                    .save(bookingCaptor.capture());

            Booking capturedBooking = bookingCaptor.getValue();

            assertAll(
                    () -> assertEquals(userId, capturedBooking.getUserId()),
                    () -> assertEquals(eventId, capturedBooking.getEventId()),
                    () -> assertEquals(2, capturedBooking.getTicketQuantity()),
                    () -> assertEquals(
                            new BigDecimal("3000.00"),
                            capturedBooking.getTotalPrice()),
                    () -> assertEquals(
                            BookingStatus.PENDING_PAYMENT,
                            capturedBooking.getStatus()),
                    () -> assertEquals(
                            "idem-key-123",
                            capturedBooking.getIdempotencyKey()),
                    () -> assertTrue(
                            capturedBooking.getBookingReference()
                                    .matches("BK-\\d{8}-[A-F0-9]{4}"))
            );

            ArgumentCaptor<BookingCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(BookingCreatedEvent.class);

            verify(jsonObjectMapper)
                    .writeValueAsString(eventCaptor.capture());

            BookingCreatedEvent capturedEvent = eventCaptor.getValue();

            assertAll(
                    () -> assertEquals(bookingId, capturedEvent.getBookingId()),
                    () -> assertEquals(userId, capturedEvent.getUserId()),
                    () -> assertEquals(eventId, capturedEvent.getEventId()),
                    () -> assertEquals(2, capturedEvent.getTicketQuantity()),
                    () -> assertEquals("LKR", capturedEvent.getCurrency()),
                    () -> assertEquals(
                            new BigDecimal("3000.00"),
                            capturedEvent.getTotalPrice())
            );

            ArgumentCaptor<OutboxEvent> outboxCaptor =
                    ArgumentCaptor.forClass(OutboxEvent.class);

            verify(outboxEventRepository)
                    .save(outboxCaptor.capture());

            OutboxEvent outbox = outboxCaptor.getValue();

            assertAll(
                    () -> assertEquals("BOOKING", outbox.getAggregateType()),
                    () -> assertEquals("BOOKING_CREATE", outbox.getEventType()),
                    () -> assertEquals(
                            bookingId.toString(),
                            outbox.getAggregateId()),
                    () -> assertEquals(
                            "{\"bookingId\":\"test\"}",
                            outbox.getPayload()),
                    () -> assertFalse(outbox.isProcessed()),
                    () -> assertEquals(0, outbox.getRetryCount())
            );

            verify(eventResolver).getValidatedEvent(eventId, 2);
            verify(bookingRepository)
                    .findByIdempotencyKey("idem-key-123");
        }

        @Test
        @DisplayName("Should return existing booking for duplicate key")
        void shouldReturnExistingBookingForDuplicateKey() {

            when(bookingRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.of(booking));

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(response);

            BookingResponseDto result =
                    bookingService.createBooking(request, userId);

            assertNotNull(result);
            assertEquals(bookingId, result.getId());

            verifyNoInteractions(eventResolver);
            verifyNoInteractions(outboxEventRepository);
            verify(bookingRepository, never())
                    .save(any(Booking.class));
        }

        @Test
        @DisplayName("Should propagate event validation failure")
        void shouldPropagateEventValidationFailure() {

            when(bookingRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());

            when(eventResolver.getValidatedEvent(eventId, 2))
                    .thenThrow(new IllegalArgumentException(
                            "Event validation failed"));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> bookingService.createBooking(request, userId)
            );

            assertEquals(
                    "Event validation failed",
                    exception.getMessage()
            );

            verify(bookingRepository, never())
                    .save(any(Booking.class));

            verifyNoInteractions(outboxEventRepository);
        }

        @Test
        @DisplayName("Should throw exception when serialization fails")
        void shouldThrowWhenSerializationFails() {

            when(bookingRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());

            when(eventResolver.getValidatedEvent(eventId, 2))
                    .thenReturn(eventResponse);

            when(bookingRepository.save(any(Booking.class)))
                    .thenReturn(booking);

            when(jsonObjectMapper.writeValueAsString(
                    any(BookingCreatedEvent.class)))
                    .thenThrow(new RuntimeException(
                            "Serialization failed"));

            EventSerializationException exception = assertThrows(
                    EventSerializationException.class,
                    () -> bookingService.createBooking(request, userId)
            );

            assertNotNull(exception.getCause());
            assertEquals(
                    "Serialization failed",
                    exception.getCause().getMessage()
            );

            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should wrap outbox save failure")
        void shouldWrapOutboxSaveFailure() {

            when(bookingRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());

            when(eventResolver.getValidatedEvent(eventId, 2))
                    .thenReturn(eventResponse);

            when(bookingRepository.save(any(Booking.class)))
                    .thenReturn(booking);

            when(jsonObjectMapper.writeValueAsString(
                    any(BookingCreatedEvent.class)))
                    .thenReturn("{}");

            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenThrow(new RuntimeException("Database error"));

            EventSerializationException exception = assertThrows(
                    EventSerializationException.class,
                    () -> bookingService.createBooking(request, userId)
            );

            assertEquals(
                    "Database error",
                    exception.getCause().getMessage()
            );

            verify(outboxEventRepository)
                    .save(any(OutboxEvent.class));
        }
    }

    @Nested
    @DisplayName("Get Booking By ID Tests")
    class GetBookingByIdTests {

        @Test
        @DisplayName("Should return booking when ID exists")
        void shouldReturnBookingWhenFound() {

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.of(booking));

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(response);

            BookingResponseDto result =
                    bookingService.getBookingById(bookingId);

            assertNotNull(result);
            assertEquals(bookingId, result.getId());

            verify(bookingRepository).findById(bookingId);
        }

        @Test
        @DisplayName("Should throw exception when booking not found")
        void shouldThrowWhenBookingNotFound() {

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.empty());

            ResourceNotFoundException exception = assertThrows(
                    ResourceNotFoundException.class,
                    () -> bookingService.getBookingById(bookingId)
            );

            assertTrue(
                    exception.getMessage().contains(bookingId.toString())
            );

            verifyNoInteractions(objectMapper);
        }
    }

    @Nested
    @DisplayName("Get Bookings By User ID Tests")
    class GetBookingsByUserIdTests {

        @Test
        @DisplayName("Should return user bookings")
        void shouldReturnUserBookings() {

            PageRequest pageable = PageRequest.of(0, 10);

            Page<Booking> page =
                    new PageImpl<>(List.of(booking), pageable, 1);

            when(bookingRepository.findByUserId(userId, pageable))
                    .thenReturn(page);

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(response);

            BookingPaginateResponseDto result =
                    bookingService.getBookingsByUserId(userId, 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getDataCount());
            assertEquals(1, result.getDataList().size());
            assertEquals(
                    bookingId,
                    result.getDataList().getFirst().getId()
            );

            verify(bookingRepository)
                    .findByUserId(userId, pageable);
        }

        @Test
        @DisplayName("Should return empty list")
        void shouldReturnEmptyList() {

            PageRequest pageable = PageRequest.of(0, 10);

            when(bookingRepository.findByUserId(userId, pageable))
                    .thenReturn(Page.empty(pageable));

            BookingPaginateResponseDto result =
                    bookingService.getBookingsByUserId(userId, 0, 10);

            assertEquals(0, result.getDataCount());
            assertTrue(result.getDataList().isEmpty());

            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("Should reject negative page number")
        void shouldRejectNegativePage() {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> bookingService.getBookingsByUserId(
                            userId, -1, 10)
            );

            verifyNoInteractions(bookingRepository);
        }

        @Test
        @DisplayName("Should reject zero page size")
        void shouldRejectZeroPageSize() {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> bookingService.getBookingsByUserId(
                            userId, 0, 0)
            );

            verifyNoInteractions(bookingRepository);
        }
    }

    @Nested
    @DisplayName("Get All Bookings Tests")
    class GetAllBookingsTests {

        @Test
        @DisplayName("Should return all bookings")
        void shouldReturnAllBookings() {

            PageRequest pageable = PageRequest.of(0, 10);

            Page<Booking> page =
                    new PageImpl<>(List.of(booking), pageable, 1);

            when(bookingRepository.findAll(pageable))
                    .thenReturn(page);

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(response);

            BookingPaginateResponseDto result =
                    bookingService.getAllBookings(0, 10);

            assertNotNull(result);
            assertEquals(1, result.getDataCount());
            assertEquals(1, result.getDataList().size());
            assertEquals(
                    bookingId,
                    result.getDataList().getFirst().getId()
            );

            verify(bookingRepository).findAll(pageable);
        }

        @Test
        @DisplayName("Should return empty bookings")
        void shouldReturnEmptyBookings() {

            PageRequest pageable = PageRequest.of(0, 10);

            when(bookingRepository.findAll(pageable))
                    .thenReturn(Page.empty(pageable));

            BookingPaginateResponseDto result =
                    bookingService.getAllBookings(0, 10);

            assertEquals(0, result.getDataCount());
            assertTrue(result.getDataList().isEmpty());

            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("Should reject negative page")
        void shouldRejectNegativePage() {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> bookingService.getAllBookings(-1, 10)
            );

            verifyNoInteractions(bookingRepository);
        }

        @Test
        @DisplayName("Should reject zero page size")
        void shouldRejectInvalidSize() {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> bookingService.getAllBookings(0, 0)
            );

            verifyNoInteractions(bookingRepository);
        }
    }

    @Nested
    @DisplayName("Cancel Booking Tests")
    class CancelBookingTests {

        @Test
        @DisplayName("Should cancel booking successfully")
        void shouldCancelBookingSuccessfully() {

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.of(booking));

            BookingResponseDto cancelledResponse =
                    BookingResponseDto.builder()
                            .id(bookingId)
                            .userId(userId)
                            .eventId(eventId)
                            .status(BookingStatus.CANCELLED)
                            .build();

            when(objectMapper.toBookingResponse(booking))
                    .thenReturn(cancelledResponse);

            BookingResponseDto result =
                    bookingService.cancelBooking(bookingId, userId);

            assertNotNull(result);
            assertEquals(
                    BookingStatus.CANCELLED,
                    booking.getStatus()
            );
            assertEquals(
                    BookingStatus.CANCELLED,
                    result.getStatus()
            );

            verify(bookingRepository).findById(bookingId);
            verify(objectMapper).toBookingResponse(booking);
        }

        @Test
        @DisplayName("Should throw when booking not found")
        void shouldThrowWhenBookingNotFound() {

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> bookingService.cancelBooking(
                            bookingId, userId)
            );

            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("Should reject unauthorized user")
        void shouldRejectUnauthorizedUser() {

            UUID anotherUserId = UUID.randomUUID();

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.of(booking));

            assertThrows(
                    UnauthorizedAccessException.class,
                    () -> bookingService.cancelBooking(
                            bookingId, anotherUserId)
            );

            assertEquals(
                    BookingStatus.PENDING_PAYMENT,
                    booking.getStatus()
            );

            verifyNoInteractions(objectMapper);
        }

        @ParameterizedTest
        @EnumSource(
                value = BookingStatus.class,
                names = {"CANCELLED", "EXPIRED"}
        )
        @DisplayName("Should reject terminal booking states")
        void shouldRejectTerminalBookingStates(BookingStatus status) {

            booking.setStatus(status);

            when(bookingRepository.findById(bookingId))
                    .thenReturn(Optional.of(booking));

            assertThrows(
                    InvalidBookingStateException.class,
                    () -> bookingService.cancelBooking(
                            bookingId, userId)
            );

            assertEquals(status, booking.getStatus());
            verifyNoInteractions(objectMapper);
        }
    }
}
