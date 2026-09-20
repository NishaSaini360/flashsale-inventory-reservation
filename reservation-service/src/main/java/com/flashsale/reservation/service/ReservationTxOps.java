package com.flashsale.reservation.service;

import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.event.EventPublisher;
import com.flashsale.reservation.repo.ReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Separate bean so @Transactional actually applies. Self-invocation inside
 * ReservationService would bypass the Spring proxy and silently run without
 * a transaction.
 */
@Component
public class ReservationTxOps {

    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final Clock clock;

    public ReservationTxOps(ReservationRepository reservations, EventPublisher events, Clock clock) {
        this.reservations = reservations; this.events = events; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID persistPending(String sku, int qty) {
        UUID ref = UUID.randomUUID();
        Reservation saved = reservations.save(new Reservation(sku, qty, ref.toString()));
        return saved.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation activate(UUID reservationId, Instant expiresAt) {
        var r = reservations.findById(reservationId).orElseThrow();
        r.activate(expiresAt, clock.instant());
        reservations.save(r);
        events.publish(EventPublisher.RESERVATION_CREATED, reservationId.toString(),
                Map.of("sku", r.getSku(), "qty", r.getQty(), "expiresAt", expiresAt.toString()));
        return r;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID reservationId) {
        reservations.findById(reservationId).ifPresent(r -> {
            r.fail(clock.instant());
            reservations.save(r);
        });
    }

    @Transactional(readOnly = true)
    public Reservation load(UUID reservationId) {
        return reservations.findById(reservationId).orElseThrow();
    }
}
