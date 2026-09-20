package com.flashsale.reservation.service;

import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.event.EventPublisher;
import com.flashsale.reservation.repo.ReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/** Separate bean: self-invocation from ExpiryWorker.run() would bypass the proxy. */
@Component
public class ExpiryTxOps {

    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final Clock clock;

    public ExpiryTxOps(ReservationRepository reservations, EventPublisher events, Clock clock) {
        this.reservations = reservations; this.events = events; this.clock = clock;
    }

    @Transactional
    public int claimBatch(int batchSize) {
        return reservations.claimExpired(clock.instant(), batchSize);
    }

    @Transactional
    public void finishRelease(Reservation r) {
        reservations.markReleaseDone(r.getId(), clock.instant());
        events.publish(EventPublisher.RESERVATION_EXPIRED, r.getId().toString(),
                Map.of("sku", r.getSku(), "qty", r.getQty()));
    }

    @Transactional
    public void cancelStale(Reservation r) {
        reservations.tryCancel(r.getId(), r.getTenantId(), clock.instant());
        reservations.markReleaseDone(r.getId(), clock.instant());
    }
}
