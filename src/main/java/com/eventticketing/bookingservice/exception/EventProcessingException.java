package com.eventticketing.bookingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class EventProcessingException extends RuntimeException {
    public EventProcessingException(String message) {
        super(message);
    }
}