# Booking Service - Architecture

## Overview

Booking Service is responsible for managing ticket bookings and reservation-related operations in the Event Ticketing System.

## Responsibilities

- Ticket Booking & Reservation
- Booking Creation and Management
- Booking Status Management
- Ticket Availability Validation
- Booking History Management
- Publishing booking-related events through Apache Kafka

## Tech Stack

| Technology | Version / Details |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Web | Spring Boot Starter Web |
| Spring Data JPA | Spring Boot Starter Data JPA |
| Spring Validation | Spring Boot Starter Validation |
| Spring Kafka | Kafka integration |
| Apache Kafka | Event messaging |
| PostgreSQL | `booking_db` |
| Lombok | Enabled |
| Maven | Build Tool |

## Dependencies

The Booking Service uses the following main dependencies:

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-kafka`
- `postgresql`
- `lombok`
- `spring-boot-starter-test`

## Architecture

```mermaid
flowchart TD

    Client[Client / Frontend]

    BookingAPI[Booking Service API]

    BookingService[Booking Service]

    PostgreSQL[(PostgreSQL<br/>booking_db)]

    Kafka[Apache Kafka]

    EventService[Event Service]

    Client --> BookingAPI
    BookingAPI --> BookingService
    BookingService --> PostgreSQL
    BookingService --> Kafka
    Kafka --> EventService
