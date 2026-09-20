package com.flashsale.order.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Order extends TenantAwareEntity {

    @Id @GeneratedValue private UUID id;

    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(nullable = false) private String sku;
    @Column(nullable = false) private int qty;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;

    @Column(name = "reservation_expires_at") private Instant reservationExpiresAt;
    @Column(name = "allocation_ref")         private String allocationRef;
    @Column(name = "failure_reason")         private String failureReason;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;

    protected Order() {}

    public Order(UUID reservationId, String sku, int qty, Instant reservationExpiresAt, String allocationRef) {
        this.reservationId = reservationId;
        this.sku = sku;
        this.qty = qty;
        this.reservationExpiresAt = reservationExpiresAt;
        this.allocationRef = allocationRef;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void transitionTo(OrderStatus next, String reason, Instant now) {
        this.status = next;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public OrderStatus getStatus() { return status; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public String getAllocationRef() { return allocationRef; }
    public String getFailureReason() { return failureReason; }
}
