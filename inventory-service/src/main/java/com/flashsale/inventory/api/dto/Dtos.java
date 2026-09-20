package com.flashsale.inventory.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public class Dtos {

    public record CreateProductRequest(@NotBlank String sku, @NotBlank String name) {}

    public record AddStockRequest(@NotBlank String sku, @NotBlank String warehouseId,
                                  @Positive int qty) {}

    public record StockView(String sku, String warehouseId, int onHand, int reserved, int available) {}

    public record SkuStockView(String sku, int onHand, int reserved, int available,
                               java.util.List<StockView> warehouses) {}

    public record AllocateRequest(@NotBlank String allocationRef, @NotBlank String sku,
                                  @Positive int qty, Instant expiresAt) {}

    public record AllocationView(UUID id, String allocationRef, String sku, int qty,
                                 String status, Instant expiresAt) {}

    public record EventView(Long id, String type, String aggregateId, String payload, Instant occurredAt) {}
}
