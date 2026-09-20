package com.flashsale.order.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class Dtos {
    public record CreateOrderRequest(@NotNull UUID reservationId) {}

    public record OrderView(UUID orderId, UUID reservationId, String sku, int qty,
                            String status, Instant reservationExpiresAt, String failureReason) {}

    public record PaymentCallbackRequest(@NotNull String externalRef, @NotNull String outcome) {}

    public record EventView(Long id, String type, String aggregateId, String payload, Instant occurredAt) {}
}
