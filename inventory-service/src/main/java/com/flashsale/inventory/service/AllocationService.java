package com.flashsale.inventory.service;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.domain.*;
import com.flashsale.inventory.event.EventPublisher;
import com.flashsale.inventory.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    private final ProductRepository products;
    private final StockItemRepository stock;
    private final AllocationRepository allocations;
    private final AllocationLineRepository lines;
    private final EventPublisher events;
    private final Clock clock;

    public AllocationService(ProductRepository products, StockItemRepository stock,
                             AllocationRepository allocations, AllocationLineRepository lines,
                             EventPublisher events, Clock clock) {
        this.products = products; this.stock = stock; this.allocations = allocations;
        this.lines = lines; this.events = events; this.clock = clock;
    }

    /**
     * Idempotent on allocationRef. A retried HTTP call (timeout, then retry) returns the
     * existing allocation instead of allocating twice. This is what makes retry safe.
     */
    @Transactional
    public Allocation allocate(String allocationRef, String sku, int qty, Instant expiresAt) {
        String tenantId = TenantContext.require();

        var existing = allocations.findByTenantIdAndAllocationRef(tenantId, allocationRef);
        if (existing.isPresent()) {
            log.info("Idempotent replay of allocation ref={}", allocationRef);
            return existing.get();
        }

        Product product = products.findByTenantIdAndSku(tenantId, sku)
                .orElseThrow(() -> Problems.notFound("Product", sku));

        List<StockItem> items = stock.findByTenantIdAndProductIdOrderByWarehouseIdAsc(tenantId, product.getId());

        Allocation allocation = allocations.save(new Allocation(allocationRef, sku, qty, expiresAt));

        // Greedy fill, deterministic warehouse order. Ordering is what prevents deadlock
        // when two allocations span the same warehouses in opposite directions.
        int remaining = qty;
        for (StockItem item : items) {
            if (remaining == 0) break;
            int want = Math.min(remaining, item.getAvailable());
            if (want <= 0) continue;
            if (stock.tryReserve(item.getId(), tenantId, want) == 1) {
                lines.save(new AllocationLine(allocation.getId(), item.getId(), item.getWarehouseId(), want));
                remaining -= want;
            }
            // 0 rows: someone beat us to it. Fall through to the next warehouse.
        }

        if (remaining > 0) {
            int available = items.stream().mapToInt(StockItem::getAvailable).sum();
            throw Problems.insufficientStock(sku, qty, Math.max(available, 0));  // rolls back everything
        }

        events.publish(EventPublisher.STOCK_RESERVED, "Allocation", allocationRef,
                Map.of("sku", sku, "qty", qty, "allocationRef", allocationRef));

        return allocation;
    }

    /** Idempotent. Releasing an already-released or committed allocation is a no-op. */
    @Transactional
    public Allocation release(String allocationRef) {
        String tenantId = TenantContext.require();
        Allocation allocation = allocations.findByTenantIdAndAllocationRef(tenantId, allocationRef)
                .orElseThrow(() -> Problems.notFound("Allocation", allocationRef));

        if (allocations.markReleased(allocation.getId(), tenantId, clock.instant()) == 0) {
            log.info("Release no-op, ref={} already {}", allocationRef, allocation.getStatus());
            return allocation;
        }

        for (AllocationLine line : lines.findByAllocationId(allocation.getId())) {
            if (stock.releaseReserved(line.getStockItemId(), tenantId, line.getQty()) != 1) {
                throw new IllegalStateException("Release would break the invariant for line " + line.getId());
            }
        }

        events.publish(EventPublisher.STOCK_RELEASED, "Allocation", allocationRef,
                Map.of("sku", allocation.getSku(), "qty", allocation.getQty(), "allocationRef", allocationRef));

        return allocations.findByTenantIdAndAllocationRef(tenantId, allocationRef).orElseThrow();
    }

    /** Commit: on_hand and reserved both drop by qty, so `available` is unchanged. */
    @Transactional
    public Allocation commit(String allocationRef) {
        String tenantId = TenantContext.require();
        Allocation allocation = allocations.findByTenantIdAndAllocationRef(tenantId, allocationRef)
                .orElseThrow(() -> Problems.notFound("Allocation", allocationRef));

        if (allocations.markCommitted(allocation.getId(), tenantId, clock.instant()) == 0) {
            if (allocation.getStatus() == AllocationStatus.COMMITTED) return allocation;  // idempotent
            throw Problems.invalidState("Allocation " + allocationRef + " is " + allocation.getStatus()
                    + " and cannot be committed");
        }

        for (AllocationLine line : lines.findByAllocationId(allocation.getId())) {
            if (stock.commitReserved(line.getStockItemId(), tenantId, line.getQty()) != 1) {
                throw new IllegalStateException("Commit would break the invariant for line " + line.getId());
            }
        }

        events.publish(EventPublisher.STOCK_COMMITTED, "Allocation", allocationRef,
                Map.of("sku", allocation.getSku(), "qty", allocation.getQty(), "allocationRef", allocationRef));

        return allocations.findByTenantIdAndAllocationRef(tenantId, allocationRef).orElseThrow();
    }
}
