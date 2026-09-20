package com.flashsale.reservation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public class Dtos {
    public record CreateReservationRequest(@NotBlank String sku, @Positive int qty) {}

    public record ReservationView(UUID reservationId, String sku, int qty,
                                  String status, Instant expiresAt, String allocationRef) {}

    public record EventView(Long id, String type, String aggregateId, String payload, Instant occurredAt) {}
}
