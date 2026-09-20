package com.flashsale.inventory.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class StockItem extends TenantAwareEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "warehouse_id", nullable = false) private String warehouseId;
    @Column(name = "on_hand", nullable = false) private int onHand;
    @Column(nullable = false) private int reserved;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

    protected StockItem() {}

    public StockItem(UUID productId, String warehouseId, int onHand) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.onHand = onHand;
        this.reserved = 0;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getWarehouseId() { return warehouseId; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public int getAvailable() { return onHand - reserved; }   // derived, never stored
}
