
package com.eventticketing.bookingservice.intergration;

import com.eventticketing.bookingservice.dto.event.BookingCreatedEvent;
import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.EventResponseDto;
import com.eventticketing.bookingservice.exception.EventSerializationException;
import com.eventticketing.bookingservice.repository.BookingRepository;
import com.eventticketing.bookingservice.repository.OutboxEventRepository;
import com.eventticketing.bookingservice.service.BookingService;
import com.eventticketing.bookingservice.service.impl.EventResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Booking Transaction Integration Tests")
class BookingTransactionIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private EventResolver eventResolver;

    @MockitoSpyBean
    private ObjectMapper jsonObjectMapper;

    @Test
    @DisplayName("Should rollback booking when outbox serialization fails")
    void shouldRollbackBookingWhenOutboxSerializationFails() {

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String idempotencyKey =
                "rollback-test-" + UUID.randomUUID();

        CreateBookingRequestDto request =
                CreateBookingRequestDto.builder()
                        .eventId(eventId)
                        .ticketQuantity(2)
                        .idempotencyKey(idempotencyKey)
                        .build();

        EventResponseDto eventResponse =
                EventResponseDto.builder()
                        .id(eventId)
                        .ticketPrice(new BigDecimal("2500.00"))
                        .availableTickets(50)
                        .build();

        when(eventResolver.getValidatedEvent(eventId, 2))
                .thenReturn(eventResponse);

        long bookingCountBefore = bookingRepository.count();
        long outboxCountBefore = outboxEventRepository.count();

        assertFalse(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        );

        doAnswer(invocation -> {

            assertTrue(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            );

            assertTrue(
                    bookingRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .isPresent()
            );

            throw new RuntimeException(
                    "Simulated serialization failure"
            );

        }).when(jsonObjectMapper)
                .writeValueAsString(any(BookingCreatedEvent.class));

        EventSerializationException exception = assertThrows(
                EventSerializationException.class,
                () -> bookingService.createBooking(request, userId)
        );

        assertFalse(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        );

        assertAll(
                () -> assertNotNull(exception.getCause()),

                () -> assertEquals(
                        "Simulated serialization failure",
                        exception.getCause().getMessage()
                ),

                () -> assertTrue(
                        bookingRepository
                                .findByIdempotencyKey(idempotencyKey)
                                .isEmpty()
                ),

                () -> assertEquals(
                        bookingCountBefore,
                        bookingRepository.count()
                ),

                () -> assertEquals(
                        outboxCountBefore,
                        outboxEventRepository.count()
                )
        );

        verify(jsonObjectMapper, times(1))
                .writeValueAsString(any(BookingCreatedEvent.class));
    }
}
