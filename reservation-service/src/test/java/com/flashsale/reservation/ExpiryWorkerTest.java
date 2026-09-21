package com.flashsale.reservation;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.repo.ReservationRepository;
import com.flashsale.reservation.service.ExpiryTxOps;
import com.flashsale.reservation.service.ExpiryWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ExpiryWorkerTest {

    @Test
    void worker_routes_state_changes_through_transactional_ops() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        InventoryClient inventory = mock(InventoryClient.class);
        ExpiryTxOps txOps = mock(ExpiryTxOps.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC);
        ExpiryWorker worker = new ExpiryWorker(reservations, inventory, txOps, clock, 100);

        Reservation pendingRelease = reservation("release-ref");
        Reservation stalePending = reservation("stale-ref");

        when(txOps.claimBatch(100)).thenReturn(1);
        when(reservations.findPendingReleases(100)).thenReturn(List.of(pendingRelease));
        when(reservations.findStalePending(clock.instant().minusSeconds(60), 100))
                .thenReturn(List.of(stalePending));

        worker.run();

        verify(txOps).claimBatch(100);
        verify(inventory).release("release-ref");
        verify(txOps).finishRelease(pendingRelease);
        verify(inventory).release("stale-ref");
        verify(txOps).cancelStale(stalePending);
        verify(reservations, never()).markReleaseDone(any(), any());
        verify(reservations, never()).tryCancel(any(), any(), any());
        assert TenantContext.get() == null;
    }

    private Reservation reservation(String allocationRef) {
        Reservation reservation = new Reservation("SKU-" + UUID.randomUUID(), 1, allocationRef);
        reservation.setTenantId("tenant-" + UUID.randomUUID());
        return reservation;
    }
}
