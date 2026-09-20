package com.flashsale.inventory.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "domain_events")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DomainEvent extends TenantAwareEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String type;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", insertable = false, updatable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected DomainEvent() {}

    public DomainEvent(String type, String aggregateType, String aggregateId, String payload) {
        this.type = type;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
}
