package com.eventticketing.bookingservice.repository;

import com.eventticketing.bookingservice.entity.ProcessedPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedPaymentEventRepository
        extends JpaRepository<ProcessedPaymentEvent, UUID> {
}