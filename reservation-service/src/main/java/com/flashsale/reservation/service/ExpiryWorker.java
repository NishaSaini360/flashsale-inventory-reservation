package com.flashsale.reservation.service;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.event.EventPublisher;
import com.flashsale.reservation.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

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
    private final EventPublisher events;
    private final Clock clock;
    private final int batchSize;

    public ExpiryWorker(ReservationRepository reservations, InventoryClient inventory,
                        EventPublisher events, Clock clock,
                        @Value("${app.expiry.batch-size}") int batchSize) {
        this.reservations = reservations; this.inventory = inventory;
        this.events = events; this.clock = clock; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.expiry.interval-ms}")
    public void run() {
        try {
            int claimed = claimBatch();
            if (claimed > 0) log.info("Expired {} reservations", claimed);
            processPendingReleases();
            recoverStalePending();
        } catch (Exception e) {
            log.error("Expiry worker pass failed", e);
        }
    }

    @Transactional
    protected int claimBatch() {
        return reservations.claimExpired(clock.instant(), batchSize);
    }

    /** Release + event, per reservation, each in its own transaction. */
    protected void processPendingReleases() {
        for (Reservation r : reservations.findPendingReleases(batchSize)) {
            try {
                TenantContext.set(r.getTenantId());
                inventory.release(r.getAllocationRef());
                finishRelease(r);
            } catch (Exception e) {
                log.error("Release failed for reservation {}, will retry", r.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    protected void finishRelease(Reservation r) {
        reservations.markReleaseDone(r.getId(), clock.instant());
        events.publish(EventPublisher.RESERVATION_EXPIRED, r.getId().toString(),
                Map.of("sku", r.getSku(), "qty", r.getQty()));
    }

    /** Crash between allocate and activate: release the orphan. */
    protected void recoverStalePending() {
        var cutoff = clock.instant().minus(Duration.ofMinutes(1));
        for (Reservation r : reservations.findStalePending(cutoff, batchSize)) {
            try {
                TenantContext.set(r.getTenantId());
                inventory.release(r.getAllocationRef());
                reservations.tryCancel(r.getId(), r.getTenantId(), clock.instant());
                reservations.markReleaseDone(r.getId(), clock.instant());
                log.warn("Recovered stale PENDING reservation {}", r.getId());
            } catch (Exception e) {
                log.error("Stale recovery failed for {}", r.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
