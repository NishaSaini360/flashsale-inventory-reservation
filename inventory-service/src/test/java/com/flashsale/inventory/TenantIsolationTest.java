package com.flashsale.inventory;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.repo.ProductRepository;
import com.flashsale.inventory.repo.StockItemRepository;
import com.flashsale.inventory.service.AllocationService;
import com.flashsale.inventory.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Two tenants owning the SAME SKU string. Draining tenant A's stock must leave
 * tenant B untouched. If tenant scoping lived only in controllers, this fails.
 */
class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired AllocationService allocations;
    @Autowired CatalogService catalog;
    @Autowired StockItemRepository stockRepo;
    @Autowired ProductRepository productRepo;
    @Autowired TransactionTemplate tx;

    @Test
    void same_sku_different_tenants_are_isolated() {
        String sku = "SHARED-" + UUID.randomUUID();
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();

        setup(tenantA, sku, 10);
        setup(tenantB, sku, 10);

        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantA);
            allocations.allocate("a-ref-" + UUID.randomUUID(), sku, 10, null);
            TenantContext.clear();
        });

        assertAvailable(tenantA, sku, 0);
        assertAvailable(tenantB, sku, 10);   // B never moved

        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantB);
            allocations.allocate("b-ref-" + UUID.randomUUID(), sku, 10, null);
            TenantContext.clear();
        });

        assertAvailable(tenantB, sku, 0);
    }

    @Test
    void tenant_cannot_see_another_tenants_product() {
        String sku = "PRIV-" + UUID.randomUUID();
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();

        setup(tenantA, sku, 5);

        // No tx wrapper: the expected exception marks the transaction rollback-only,
        // which would then fail the commit rather than the assertion.
        TenantContext.set(tenantB);
        try {
            assertThatThrownBy(() -> catalog.stockFor(sku))
                    .isInstanceOf(DomainException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void same_allocation_ref_in_two_tenants_are_independent() {
        String sku = "REF-" + UUID.randomUUID();
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();
        String sharedRef = "shared-ref-" + UUID.randomUUID();

        setup(tenantA, sku, 5);
        setup(tenantB, sku, 5);

        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantA);
            allocations.allocate(sharedRef, sku, 5, null);
            TenantContext.clear();
        });

        // Same ref string, different tenant: must NOT be treated as an idempotent replay.
        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantB);
            allocations.allocate(sharedRef, sku, 5, null);
            TenantContext.clear();
        });

        assertAvailable(tenantA, sku, 0);
        assertAvailable(tenantB, sku, 0);   // each consumed its own stock
    }

    private void setup(String tenant, String sku, int qty) {
        tx.executeWithoutResult(s -> {
            TenantContext.set(tenant);
            catalog.createProduct(sku, "Shared Widget");
            catalog.addStock(sku, "wh-1", qty);
            TenantContext.clear();
        });
    }

    private void assertAvailable(String tenant, String sku, int expected) {
        tx.executeWithoutResult(s -> {
            TenantContext.set(tenant);
            var product = productRepo.findByTenantIdAndSku(tenant, sku).orElseThrow();
            var item = stockRepo.findByTenantIdAndProductIdOrderByWarehouseIdAsc(tenant, product.getId()).getFirst();
            assertThat(item.getAvailable()).isEqualTo(expected);
            TenantContext.clear();
        });
    }
}
