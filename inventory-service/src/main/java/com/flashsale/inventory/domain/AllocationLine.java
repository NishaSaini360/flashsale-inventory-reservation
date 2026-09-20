package com.flashsale.inventory.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "allocation_lines")
public class AllocationLine {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "allocation_id", nullable = false) private UUID allocationId;
    @Column(name = "stock_item_id", nullable = false) private UUID stockItemId;
    @Column(name = "warehouse_id", nullable = false)  private String warehouseId;
    @Column(nullable = false) private int qty;

    protected AllocationLine() {}

    public AllocationLine(UUID allocationId, UUID stockItemId, String warehouseId, int qty) {
        this.allocationId = allocationId;
        this.stockItemId = stockItemId;
        this.warehouseId = warehouseId;
        this.qty = qty;
    }

    public UUID getId() { return id; }
    public UUID getAllocationId() { return allocationId; }
    public UUID getStockItemId() { return stockItemId; }
    public String getWarehouseId() { return warehouseId; }
    public int getQty() { return qty; }
}
