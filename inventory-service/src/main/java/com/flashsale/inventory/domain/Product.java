package com.flashsale.inventory.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Product extends TenantAwareEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false) private String sku;
    @Column(nullable = false) private String name;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Product() {}

    public Product(String sku, String name) {
        this.sku = sku;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
