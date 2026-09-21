package com.flashsale.reservation.service;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Expiry is NOT on-read (the brief forbids it). This worker claims expired rows with
 * FOR UPDATE SKIP LOCKED, so multiple instances can run it safely without ShedLock.
 * Releases that fail are retried by the second pass — a mini-outbox without a broker.
 */
@Component
public class ExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(ExpiryWorker.class);

    private final ReservationRepository reservations;
    private final InventoryClient inventory;
    private final ExpiryTxOps txOps;
    private final Clock clock;
    private final int batchSize;

    public ExpiryWorker(ReservationRepository reservations, InventoryClient inventory,
                        ExpiryTxOps txOps, Clock clock,
                        @Value("${app.expiry.batch-size}") int batchSize) {
        this.reservations = reservations; this.inventory = inventory;
        this.txOps = txOps; this.clock = clock; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.expiry.interval-ms}")
    public void run() {
        try {
            int claimed = txOps.claimBatch(batchSize);
            if (claimed > 0) log.info("Expired {} reservations", claimed);
            processPendingReleases();
            recoverStalePending();
        } catch (Exception e) {
            log.error("Expiry worker pass failed", e);
        }
    }

    /** Release + event, per reservation, each in its own transaction. */
    protected void processPendingReleases() {
        for (Reservation r : reservations.findPendingReleases(batchSize)) {
            try {
                TenantContext.set(r.getTenantId());
                inventory.release(r.getAllocationRef());
                txOps.finishRelease(r);
            } catch (Exception e) {
                log.error("Release failed for reservation {}, will retry", r.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Crash between allocate and activate: release the orphan. */
    protected void recoverStalePending() {
        var cutoff = clock.instant().minus(Duration.ofMinutes(1));
        for (Reservation r : reservations.findStalePending(cutoff, batchSize)) {
            try {
                TenantContext.set(r.getTenantId());
                inventory.release(r.getAllocationRef());
                txOps.cancelStale(r);
                log.warn("Recovered stale PENDING reservation {}", r.getId());
            } catch (Exception e) {
                log.error("Stale recovery failed for {}", r.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
