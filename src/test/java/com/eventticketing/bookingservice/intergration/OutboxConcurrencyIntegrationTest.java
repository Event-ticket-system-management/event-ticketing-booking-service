package com.eventticketing.bookingservice.intergration;

import com.eventticketing.bookingservice.entity.OutboxEvent;
import com.eventticketing.bookingservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("PostgreSQL Outbox Concurrency Integration Tests")
class OutboxConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("booking_concurrency_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );

        registry.add(
                "spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );

        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Should skip rows locked by another transaction")
    void shouldSkipRowsLockedByAnotherTransaction()
            throws Exception {

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        List<OutboxEvent> testEvents =
                IntStream.range(0, 3)
                        .mapToObj(index -> createOutboxEvent())
                        .toList();

        List<OutboxEvent> savedEvents =
                outboxEventRepository.saveAllAndFlush(testEvents);

        Set<UUID> expectedIds = savedEvents.stream()
                .map(OutboxEvent::getId)
                .collect(Collectors.toSet());

        assertEquals(3, expectedIds.size());

        CountDownLatch rowsLocked = new CountDownLatch(1);
        CountDownLatch allowWorkerACommit = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Future<Set<UUID>> workerA = executor.submit(() ->
                    transactionTemplate.execute(status -> {

                        List<OutboxEvent> events =
                                outboxEventRepository
                                        .findUnprocessedEventsForUpdate();

                        Set<UUID> selectedIds = events.stream()
                                .map(OutboxEvent::getId)
                                .collect(Collectors.toSet());

                        assertEquals(expectedIds, selectedIds);

                        rowsLocked.countDown();

                        awaitLatch(
                                allowWorkerACommit,
                                "Worker A commit permission"
                        );

                        for (OutboxEvent event : events) {
                            event.setProcessed(true);
                            event.setProcessedAt(LocalDateTime.now());
                        }

                        outboxEventRepository.saveAllAndFlush(events);

                        return selectedIds;
                    })
            );

            awaitLatch(rowsLocked, "Worker A row locks");

            Future<Set<UUID>> workerB = executor.submit(() ->
                    transactionTemplate.execute(status ->
                            outboxEventRepository
                                    .findUnprocessedEventsForUpdate()
                                    .stream()
                                    .map(OutboxEvent::getId)
                                    .collect(Collectors.toSet())
                    )
            );

            Set<UUID> workerBIds =
                    workerB.get(5, TimeUnit.SECONDS);

            assertNotNull(workerBIds);

            assertTrue(
                    workerBIds.isEmpty(),
                    "Worker B must skip rows locked by Worker A"
            );

            assertFalse(
                    workerA.isDone(),
                    "Worker A must still be holding its transaction"
            );

            allowWorkerACommit.countDown();

            Set<UUID> workerAIds =
                    workerA.get(10, TimeUnit.SECONDS);

            assertEquals(expectedIds, workerAIds);

            List<OutboxEvent> remainingEvents =
                    transactionTemplate.execute(status ->
                            outboxEventRepository
                                    .findUnprocessedEventsForUpdate()
                    );

            assertNotNull(remainingEvents);

            assertTrue(
                    remainingEvents.isEmpty(),
                    "Processed events must not be selected again"
            );

            List<OutboxEvent> processedEvents =
                    outboxEventRepository.findAllById(expectedIds);

            assertEquals(3, processedEvents.size());

            assertTrue(
                    processedEvents.stream()
                            .allMatch(OutboxEvent::isProcessed)
            );

            assertTrue(
                    processedEvents.stream()
                            .allMatch(event ->
                                    event.getProcessedAt() != null)
            );

            System.out.println(
                    "Worker A selected: " + workerAIds.size()
            );

            System.out.println(
                    "Worker B selected while rows locked: "
                            + workerBIds.size()
            );

            System.out.println(
                    "Remaining unprocessed events: "
                            + remainingEvents.size()
            );

            System.out.println(
                    "Successfully processed events: "
                            + processedEvents.size()
            );

        } finally {

            allowWorkerACommit.countDown();

            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(15, TimeUnit.SECONDS),
                    "Worker threads did not terminate"
            );
        }
    }

    private OutboxEvent createOutboxEvent() {

        UUID bookingId = UUID.randomUUID();

        return OutboxEvent.builder()
                .aggregateType("BOOKING")
                .aggregateId(bookingId.toString())
                .eventType("BOOKING_CREATE")
                .payload("""
                        {"bookingId":"%s"}
                        """.formatted(bookingId))
                .processed(false)
                .retryCount(0)
                .build();
    }

    private static void awaitLatch(
            CountDownLatch latch,
            String description
    ) {
        try {

            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for " + description
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while waiting for " + description,
                    exception
            );
        }
    }
}
