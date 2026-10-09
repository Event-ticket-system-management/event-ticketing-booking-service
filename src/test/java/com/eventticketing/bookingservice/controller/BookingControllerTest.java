
package com.eventticketing.bookingservice.controller;

import com.eventticketing.bookingservice.dto.request.CreateBookingRequestDto;
import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import com.eventticketing.bookingservice.dto.response.paginate.BookingPaginateResponseDto;
import com.eventticketing.bookingservice.enums.BookingStatus;
import com.eventticketing.bookingservice.security.JwtAuthenticationFilter;
import com.eventticketing.bookingservice.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(BookingControllerTest.TestSecurityConfiguration.class)
@DisplayName("BookingController Web MVC Tests")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;


    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BookingService bookingService;

    private UUID userId;
    private UUID bookingId;
    private UUID eventId;

    private BookingResponseDto bookingResponseDto;
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {

            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                            )
                    )
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        bookingResponseDto = BookingResponseDto.builder()
                .id(bookingId)
                .bookingReference("BK-20261009-8A2F")
                .idempotencyKey("idem-key-123")
                .userId(userId)
                .eventId(eventId)
                .ticketQuantity(2)
                .totalPrice(new BigDecimal("3000.00"))
                .status(BookingStatus.PENDING_PAYMENT)
                .build();
    }

    private Authentication userAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private CreateBookingRequestDto validRequest() {
        return CreateBookingRequestDto.builder()
                .eventId(eventId)
                .ticketQuantity(2)
                .idempotencyKey("idem-key-123")
                .build();
    }

    private BookingPaginateResponseDto paginatedResponse() {
        return BookingPaginateResponseDto.builder()
                .dataCount(1)
                .dataList(List.of(bookingResponseDto))
                .build();
    }

    @Nested
    @DisplayName("Create Booking Tests")
    class CreateBookingTests {

        @Test
        void shouldCreateBookingSuccessfully() throws Exception {
            when(bookingService.createBooking(
                    any(CreateBookingRequestDto.class),
                    eq(userId)
            )).thenReturn(bookingResponseDto);

            mockMvc.perform(post("/api/v1/bookings")
                            .with(authentication(userAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id")
                            .value(bookingId.toString()))
                    .andExpect(jsonPath("$.bookingReference")
                            .value("BK-20261009-8A2F"))
                    .andExpect(jsonPath("$.status")
                            .value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.totalPrice")
                            .value(3000.00));

            verify(bookingService).createBooking(
                    argThat(request ->
                            eventId.equals(request.getEventId())
                                    && request.getTicketQuantity() == 2
                                    && "idem-key-123".equals(
                                    request.getIdempotencyKey())
                    ),
                    eq(userId)
            );
        }

        @Test
        void shouldRejectInvalidBookingRequest() throws Exception {
            CreateBookingRequestDto request =
                    CreateBookingRequestDto.builder()
                            .eventId(eventId)
                            .ticketQuantity(0)
                            .idempotencyKey("")
                            .build();

            mockMvc.perform(post("/api/v1/bookings")
                            .with(authentication(userAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/api/v1/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    validRequest())))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldRejectMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/bookings")
                            .with(authentication(userAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid-json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }
    }

    @Nested
    @DisplayName("Get Booking By ID Tests")
    class GetBookingByIdTests {

        @Test
        void shouldReturnBookingSuccessfully() throws Exception {
            when(bookingService.getBookingById(bookingId))
                    .thenReturn(bookingResponseDto);

            mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id")
                            .value(bookingId.toString()))
                    .andExpect(jsonPath("$.totalPrice")
                            .value(3000.00));

            verify(bookingService).getBookingById(bookingId);
        }

        @Test
        void shouldRejectInvalidBookingId() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/{id}", "invalid-uuid")
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/{id}", bookingId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(bookingService);
        }
    }

    @Nested
    @DisplayName("Get My Bookings Tests")
    class GetMyBookingsTests {

        @Test
        void shouldReturnMyBookingsSuccessfully() throws Exception {
            when(bookingService.getBookingsByUserId(userId, 0, 10))
                    .thenReturn(paginatedResponse());

            mockMvc.perform(get("/api/v1/bookings/my-bookings")
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataCount").value(1))
                    .andExpect(jsonPath("$.dataList[0].id")
                            .value(bookingId.toString()));

            verify(bookingService)
                    .getBookingsByUserId(userId, 0, 10);
        }

        @Test
        void shouldApplyCustomPagination() throws Exception {
            when(bookingService.getBookingsByUserId(userId, 2, 5))
                    .thenReturn(paginatedResponse());

            mockMvc.perform(get("/api/v1/bookings/my-bookings")
                            .with(authentication(userAuthentication()))
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataCount").value(1));

            verify(bookingService)
                    .getBookingsByUserId(userId, 2, 5);
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/v1/bookings/my-bookings"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(bookingService);
        }
    }

    @Nested
    @DisplayName("Get All Bookings Tests")
    class GetAllBookingsTests {

        @Test
        void shouldAllowAdminToGetAllBookings() throws Exception {
            when(bookingService.getAllBookings(0, 10))
                    .thenReturn(paginatedResponse());

            mockMvc.perform(get("/api/v1/bookings")
                            .with(authentication(adminAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataCount").value(1))
                    .andExpect(jsonPath("$.dataList[0].id")
                            .value(bookingId.toString()));

            verify(bookingService).getAllBookings(0, 10);
        }

        @Test
        void shouldRejectNonAdminUser() throws Exception {
            mockMvc.perform(get("/api/v1/bookings")
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/v1/bookings"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldApplyCustomPaginationForAdmin() throws Exception {
            when(bookingService.getAllBookings(1, 20))
                    .thenReturn(paginatedResponse());

            mockMvc.perform(get("/api/v1/bookings")
                            .with(authentication(adminAuthentication()))
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataCount").value(1));

            verify(bookingService).getAllBookings(1, 20);
        }
    }

    @Nested
    @DisplayName("Cancel Booking Tests")
    class CancelBookingTests {

        @Test
        void shouldCancelBookingSuccessfully() throws Exception {
            bookingResponseDto.setStatus(BookingStatus.CANCELLED);

            when(bookingService.cancelBooking(bookingId, userId))
                    .thenReturn(bookingResponseDto);

            mockMvc.perform(patch(
                            "/api/v1/bookings/{id}/cancel",
                            bookingId)
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id")
                            .value(bookingId.toString()))
                    .andExpect(jsonPath("$.status")
                            .value("CANCELLED"));

            verify(bookingService)
                    .cancelBooking(bookingId, userId);
        }

        @Test
        void shouldRejectInvalidBookingId() throws Exception {
            mockMvc.perform(patch(
                            "/api/v1/bookings/{id}/cancel",
                            "invalid-uuid")
                            .with(authentication(userAuthentication())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        void shouldRejectUnauthenticatedRequest() throws Exception {
            mockMvc.perform(patch(
                            "/api/v1/bookings/{id}/cancel",
                            bookingId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(bookingService);
        }
    }
}
