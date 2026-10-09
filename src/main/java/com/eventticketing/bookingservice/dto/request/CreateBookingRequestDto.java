package com.eventticketing.bookingservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequestDto {

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotNull(message = "Ticket quantity is required")
    @Min(value = 1, message = "Ticket quantity must be at least 1")
    @Max(value = 10, message = "Maximum 10 tickets allowed per booking")
    private Integer ticketQuantity;

    @NotBlank(message = "Idempotency Key is required to prevent duplicate bookings")
    private String idempotencyKey;

}