package com.flashsale.inventory;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.repo.*;
import com.flashsale.inventory.service.AllocationService;
import com.flashsale.inventory.service.CatalogService;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OversellConcurrencyTest extends AbstractIntegrationTest {

    private static final int ON_HAND = 100;
    private static final int THREADS = 300;
    private static final String TENANT = "tenant-oversell";

    @Autowired AllocationService allocationService;
    @Autowired CatalogService catalogService;
    @Autowired StockItemRepository stockRepo;
    @Autowired ProductRepository productRepo;
    @Autowired AllocationRepository allocationRepo;
    @Autowired TransactionTemplate tx;

    @RepeatedTest(5)   // races are probabilistic; one green run proves nothing
    void concurrent_allocates_never_oversell() throws Exception {
        String sku = "FLASH-" + UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            TenantContext.set(TENANT);
            catalogService.createProduct(sku, "Flash Item");
            catalogService.addStock(sku, "wh-1", ON_HAND);
            TenantContext.clear();
        });

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(THREADS);
        var ok = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unexpected = new ConcurrentLinkedQueue<Throwable>();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        TenantContext.set(TENANT);
                        allocationService.allocate("ref-" + UUID.randomUUID(), sku, 1, null);
                        ok.incrementAndGet();
                    } catch (DomainException e) {
                        rejected.incrementAndGet();          // insufficient stock — correct
                    } catch (Throwable t) {
                        unexpected.add(t);
                    } finally {
                        TenantContext.clear();
                        done.countDown();
                    }
                });
            }
            start.countDown();                                // release them all at once
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(unexpected).isEmpty();
        assertThat(ok.get()).isEqualTo(ON_HAND);
        assertThat(rejected.get()).isEqualTo(THREADS - ON_HAND);

        tx.executeWithoutResult(s -> {
            TenantContext.set(TENANT);
            var product = productRepo.findByTenantIdAndSku(TENANT, sku).orElseThrow();
            var item = stockRepo.findByTenantIdAndProductIdOrderByWarehouseIdAsc(TENANT, product.getId()).getFirst();

            assertThat(item.getReserved()).isEqualTo(ON_HAND);
            assertThat(item.getAvailable()).isZero();
            assertThat(item.getOnHand()).isEqualTo(ON_HAND);
            assertThat(item.getReserved()).isLessThanOrEqualTo(item.getOnHand());
            TenantContext.clear();
        });
    }
}
