package com.flashsale.order.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PaymentAttempt extends TenantAwareEntity {

    @Id @GeneratedValue private UUID id;

    @Column(name = "order_id", nullable = false)     private UUID orderId;
    @Column(name = "external_ref", nullable = false) private String externalRef;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;

    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "settled_at") private Instant settledAt;

    protected PaymentAttempt() {}

    public PaymentAttempt(UUID orderId, String externalRef, long amountMinor) {
        this.orderId = orderId;
        this.externalRef = externalRef;
        this.amountMinor = amountMinor;
        this.status = PaymentStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getExternalRef() { return externalRef; }
    public long getAmountMinor() { return amountMinor; }
    public PaymentStatus getStatus() { return status; }
}
