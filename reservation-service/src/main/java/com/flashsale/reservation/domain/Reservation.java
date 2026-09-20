package com.flashsale.reservation.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Reservation extends TenantAwareEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false) private String sku;
    @Column(nullable = false) private int qty;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at")     private Instant expiresAt;
    @Column(name = "allocation_ref", nullable = false) private String allocationRef;

    @Enumerated(EnumType.STRING) @Column(name = "release_state", nullable = false)
    private ReleaseState releaseState = ReleaseState.NOT_NEEDED;

    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    protected Reservation() {}

    public Reservation(String sku, int qty, String allocationRef) {
        this.sku = sku;
        this.qty = qty;
        this.allocationRef = allocationRef;
        this.status = ReservationStatus.PENDING;
    }

    public void activate(Instant expiresAt, Instant now) {
        this.status = ReservationStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void fail(Instant now) {
        this.status = ReservationStatus.FAILED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getAllocationRef() { return allocationRef; }
    public ReleaseState getReleaseState() { return releaseState; }
}
