package com.eventticketing.bookingservice.service.impl;

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
import com.eventticketing.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;
    private final EventResolver eventResolver;
    private final OutboxEventRepository outboxEventRepository;
    private final tools.jackson.databind.ObjectMapper jsonObjectMapper;

    @Override
    @Transactional
    public BookingResponseDto createBooking(CreateBookingRequestDto request, UUID userId) {
        log.info("Initiating booking creation for userId: {}, eventId: {}", userId, request.getEventId());

        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingBooking.isPresent()) {
            log.info("Duplicate request detected with Idempotency Key: {}. Returning existing booking.", request.getIdempotencyKey());
            return objectMapper.toBookingResponse(existingBooking.get());
        }

        EventResponseDto eventDto = eventResolver.getValidatedEvent(request.getEventId(), request.getTicketQuantity());

        BigDecimal totalPrice = eventDto.getTicketPrice().multiply(BigDecimal.valueOf(request.getTicketQuantity()));

        String bookingReference = generateBookingReference();

        Booking booking = Booking.builder()
                .bookingReference(bookingReference)
                .idempotencyKey(request.getIdempotencyKey())
                .userId(userId)
                .eventId(request.getEventId())
                .ticketQuantity(request.getTicketQuantity())
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING_PAYMENT)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Booking created with ID: {} and Reference: {} in PENDING status", savedBooking.getId(), bookingReference);

        createOutboxEvent(booking);

        return objectMapper.toBookingResponse(savedBooking);

    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(UUID id) {
        log.debug("Fetching booking by ID: {}", id);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Booking not found with ID: %s", id )));
        return objectMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPaginateResponseDto getBookingsByUserId(UUID userId, int page, int size) {
        log.debug("Fetching bookings for userId: {}, page: {}, size: {}", userId, page, size);
        Page<Booking> bookingPage = bookingRepository.findByUserId(userId, PageRequest.of(page, size));

        return BookingPaginateResponseDto.builder()
                .dataCount(bookingPage.getTotalElements())
                .dataList(bookingPage.getContent().stream()
                        .map(objectMapper::toBookingResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPaginateResponseDto getAllBookings(int page, int size) {
        log.debug("Fetching all bookings, page: {}, size: {}", page, size);
        Page<Booking> bookingPage = bookingRepository.findAll(PageRequest.of(page, size));

        return BookingPaginateResponseDto.builder()
                .dataCount(bookingPage.getTotalElements())
                .dataList(bookingPage.getContent().stream()
                        .map(objectMapper::toBookingResponse)
                        .collect(Collectors.toList()))
                .build();
    }


    @Override
    @Transactional
    public BookingResponseDto cancelBooking(UUID id, UUID userId) {
        log.info("Cancelling booking ID: {} for userId: {}", id, userId);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Booking not found with ID: %s", id)));

        if (!booking.getUserId().equals(userId)){
            throw new UnauthorizedAccessException("You are not authorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED){
            throw new InvalidBookingStateException(
                    String.format("Cannot cancel or delete booking/event with ID: %s", id)
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);
        log.info("Booking ID: {} marked as CANCELLED", id);

        return objectMapper.toBookingResponse(booking);
    }

    private String generateBookingReference(){
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPrefix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("BK-%s-%s", datePrefix, randomPrefix);
    }

    private void createOutboxEvent(Booking booking){
        try {
            BookingCreatedEvent eventPayload = BookingCreatedEvent.builder()
                    .bookingId(booking.getId())
                    .bookingReference(booking.getBookingReference())
                    .idempotencyKey(booking.getIdempotencyKey())
                    .userId(booking.getUserId())
                    .eventId(booking.getEventId())
                    .ticketQuantity(booking.getTicketQuantity())
                    .totalPrice(booking.getTotalPrice())
                    .currency("LKR")
                    .createdAt(booking.getCreatedAt())
                    .build();

            String jsonPayload = jsonObjectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("BOOKING")
                    .aggregateId(booking.getId().toString())
                    .eventType("BOOKING_CREATE")
                    .payload(jsonPayload)
                    .processed(false)
                    .retryCount(0)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event created successfully for Booking ID: {}", booking.getId());


        }catch (Exception e){
            log.error("Failed to serialize BookingCreatedEvent payload for Booking ID: {}", booking.getId(), e);
            throw new EventSerializationException("Failed to process booking event payload serialization", e);
        }
    }
}
