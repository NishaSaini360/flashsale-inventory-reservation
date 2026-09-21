package com.flashsale.order;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.order.domain.Order;
import com.flashsale.order.event.EventPublisher;
import com.flashsale.order.repo.DomainEventRepository;
import com.flashsale.order.repo.OrderRepository;
import com.flashsale.order.service.OrderTxOps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired OrderRepository orders;
    @Autowired OrderTxOps txOps;
    @Autowired EventPublisher events;
    @Autowired DomainEventRepository eventRepo;
    @Autowired TransactionTemplate tx;

    @Test
    void tenant_cannot_read_another_tenants_order() {
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();

        UUID orderId = tx.execute(s -> {
            TenantContext.set(tenantA);
            try {
                Order order = new Order(UUID.randomUUID(), "SKU-" + UUID.randomUUID(), 1,
                        Instant.now().plusSeconds(60), "alloc-" + UUID.randomUUID());
                return orders.save(order).getId();
            } finally {
                TenantContext.clear();
            }
        });

        TenantContext.set(tenantB);
        try {
            assertThatThrownBy(() -> txOps.load(orderId))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Access denied");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void event_reads_are_tenant_scoped() {
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantA);
            try {
                events.publish(EventPublisher.ORDER_CREATED, "order-a",
                        Map.of("marker", tenantA));
            } finally {
                TenantContext.clear();
            }
        });
        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantB);
            try {
                events.publish(EventPublisher.ORDER_CREATED, "order-b",
                        Map.of("marker", tenantB));
            } finally {
                TenantContext.clear();
            }
        });

        var tenantAEvents = eventRepo.findTop200ByTenantIdOrderByIdDesc(tenantA);

        assertThat(tenantAEvents).extracting("aggregateId").contains("order-a");
        assertThat(tenantAEvents).extracting("aggregateId").doesNotContain("order-b");
    }
}
