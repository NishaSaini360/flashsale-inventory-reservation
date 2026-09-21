package com.flashsale.reservation;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.event.EventPublisher;
import com.flashsale.reservation.repo.DomainEventRepository;
import com.flashsale.reservation.repo.ReservationRepository;
import com.flashsale.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired ReservationRepository reservations;
    @Autowired ReservationService service;
    @Autowired EventPublisher events;
    @Autowired DomainEventRepository eventRepo;
    @Autowired TransactionTemplate tx;

    @MockBean com.flashsale.reservation.client.InventoryClient inventory;

    @Test
    void tenant_cannot_read_another_tenants_reservation() {
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();

        UUID reservationId = tx.execute(s -> {
            TenantContext.set(tenantA);
            try {
                Reservation reservation = new Reservation("SKU-" + UUID.randomUUID(), 1,
                        "alloc-" + UUID.randomUUID());
                reservation.activate(Instant.now().plusSeconds(60), Instant.now());
                return reservations.save(reservation).getId();
            } finally {
                TenantContext.clear();
            }
        });

        TenantContext.set(tenantB);
        try {
            assertThatThrownBy(() -> service.get(reservationId))
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
                events.publish(EventPublisher.RESERVATION_CREATED, "res-a",
                        Map.of("marker", tenantA));
            } finally {
                TenantContext.clear();
            }
        });
        tx.executeWithoutResult(s -> {
            TenantContext.set(tenantB);
            try {
                events.publish(EventPublisher.RESERVATION_CREATED, "res-b",
                        Map.of("marker", tenantB));
            } finally {
                TenantContext.clear();
            }
        });

        var tenantAEvents = eventRepo.findTop200ByTenantIdOrderByIdDesc(tenantA);

        assertThat(tenantAEvents).extracting("aggregateId").contains("res-a");
        assertThat(tenantAEvents).extracting("aggregateId").doesNotContain("res-b");
    }
}
