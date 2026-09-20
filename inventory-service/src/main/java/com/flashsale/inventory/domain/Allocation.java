package com.flashsale.inventory.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "allocations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Allocation extends TenantAwareEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "allocation_ref", nullable = false) private String allocationRef;
    @Column(nullable = false) private String sku;
    @Column(nullable = false) private int qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private AllocationStatus status;

    @Column(name = "expires_at")   private Instant expiresAt;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "released_at")  private Instant releasedAt;
    @Column(name = "committed_at") private Instant committedAt;

    @OneToMany(mappedBy = "allocationId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AllocationLine> lines = new ArrayList<>();

    protected Allocation() {}

    public Allocation(String allocationRef, String sku, int qty, Instant expiresAt) {
        this.allocationRef = allocationRef;
        this.sku = sku;
        this.qty = qty;
        this.expiresAt = expiresAt;
        this.status = AllocationStatus.ALLOCATED;
    }

    public UUID getId() { return id; }
    public String getAllocationRef() { return allocationRef; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public AllocationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public List<AllocationLine> getLines() { return lines; }
}
