package com.flashsale.inventory;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.repo.ProductRepository;
import com.flashsale.inventory.repo.StockItemRepository;
import com.flashsale.inventory.service.AllocationService;
import com.flashsale.inventory.service.CatalogService;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Double-release is the sneakiest oversell path: two releases decrement `reserved`
 * twice and inflate availability. The guarded status transition must make exactly
 * one of them win.
 */
class DoubleReleaseTest extends AbstractIntegrationTest {

    private static final String TENANT = "tenant-release";

    @Autowired AllocationService allocationService;
    @Autowired CatalogService catalogService;
    @Autowired StockItemRepository stockRepo;
    @Autowired ProductRepository productRepo;
    @Autowired TransactionTemplate tx;

    @RepeatedTest(5)
    void concurrent_releases_decrement_reserved_exactly_once() throws Exception {
        String sku = "REL-" + UUID.randomUUID();
        String ref = "ref-" + UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            TenantContext.set(TENANT);
            catalogService.createProduct(sku, "Release Item");
            catalogService.addStock(sku, "wh-1", 10);
            allocationService.allocate(ref, sku, 4, null);
            TenantContext.clear();
        });

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(8);

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        TenantContext.set(TENANT);
                        allocationService.release(ref);
                    } catch (Exception ignored) {
                    } finally {
                        TenantContext.clear();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        tx.executeWithoutResult(s -> {
            TenantContext.set(TENANT);
            var product = productRepo.findBySku(sku).orElseThrow();
            var item = stockRepo.findByProductIdOrderByWarehouseIdAsc(product.getId()).getFirst();

            assertThat(item.getReserved()).isZero();          // released exactly once, not 8 times
            assertThat(item.getOnHand()).isEqualTo(10);
            assertThat(item.getAvailable()).isEqualTo(10);    // never inflated above on_hand
            TenantContext.clear();
        });
    }
}
