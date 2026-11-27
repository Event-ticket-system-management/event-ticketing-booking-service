package com.eventticketing.bookingservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorConfiguration {

    @Bean
    public DefaultErrorHandler defaultErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) -> {
                    throw new IllegalStateException(String.format(
                            "Kafka event retries exhausted: %s "
                                    , record.topic() ),
                            exception
                    );
                },
                new FixedBackOff(1000L, 3L)
        );
    }
}

