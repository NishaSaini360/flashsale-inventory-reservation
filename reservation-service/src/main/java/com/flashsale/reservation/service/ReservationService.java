package com.flashsale.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.IdempotencyRecord;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.domain.ReservationStatus;
import com.flashsale.reservation.event.EventPublisher;
import com.flashsale.reservation.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservations;
    private final InventoryClient inventory;
    private final IdempotencyService idempotency;
    private final EventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration ttl;
    private final Duration grace;
    private final ReservationTxOps txOps;

    public ReservationService(ReservationRepository reservations, InventoryClient inventory,
                              IdempotencyService idempotency, EventPublisher events,
                              ObjectMapper mapper, Clock clock,
                              @Value("${app.reservation.ttl}") Duration ttl,
                              @Value("${app.reservation.allocation-grace}") Duration grace,
                              ReservationTxOps txOps) {
        this.reservations = reservations;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
        this.ttl = ttl;
        this.grace = grace;
        this.txOps = txOps;
    }

    /**
     * Deliberately NOT @Transactional. The brief forbids holding a DB transaction
     * across an HTTP call, so this runs as three short local transactions (via txOps)
     * with the network call sitting between them.
     */
    public ReservationResult reserve(String sku, int qty, String idemKey, String rawBody) {
        String hash = idempotency.hash(rawBody);

        if (!idempotency.claim(idemKey, hash)) {
            var existing = idempotency.find(idemKey).orElseThrow();
            idempotency.assertSamePayload(existing, hash);
            if (existing.getState() == IdempotencyRecord.State.COMPLETED) {
                return ReservationResult.replay(existing.getResponseStatus(), existing.getResponseBody());
            }
            throw Problems.idempotencyConflict(idemKey);
        }

        UUID reservationId = txOps.persistPending(sku, qty);
        Instant expiresAt = clock.instant().plus(ttl);
        String allocationRef = txOps.load(reservationId).getAllocationRef();

        try {
            // Inventory's lease outlives our TTL by `grace`, so its sweeper is the
            // last line of defence if this process dies before we mark ACTIVE.
            inventory.allocate(allocationRef, sku, qty, expiresAt.plus(grace));
        } catch (DomainException e) {
            txOps.markFailed(reservationId);
            idempotency.fail(idemKey);
            throw e;
        }

        Reservation active = txOps.activate(reservationId, expiresAt);
        idempotency.complete(idemKey, reservationId, 201, toJson(active));
        return ReservationResult.created(active);
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return reservations.findById(id).orElseThrow(() -> Problems.notFound("Reservation", id));
    }

    /** Atomic gate: ACTIVE and unexpired, or nothing. Closes the worker-lag window. */
    @Transactional
    public Reservation confirm(UUID id) {
        String tenantId = TenantContext.require();
        if (reservations.tryConfirm(id, tenantId, clock.instant()) == 0) {
            var r = reservations.findById(id).orElseThrow(() -> Problems.notFound("Reservation", id));
            if (r.getStatus() == ReservationStatus.CONFIRMED) return r;
            throw Problems.reservationExpired(id);
        }
        var r = reservations.findById(id).orElseThrow();
        events.publish(EventPublisher.RESERVATION_CONFIRMED, id.toString(),
                Map.of("sku", r.getSku(), "qty", r.getQty()));
        return r;
    }

    @Transactional
    public Reservation cancel(UUID id) {
        String tenantId = TenantContext.require();
        if (reservations.tryCancel(id, tenantId, clock.instant()) == 0) {
            return reservations.findById(id).orElseThrow(() -> Problems.notFound("Reservation", id));
        }
        var r = reservations.findById(id).orElseThrow();
        events.publish(EventPublisher.RESERVATION_CANCELLED, id.toString(),
                Map.of("sku", r.getSku(), "qty", r.getQty()));
        return r;
    }

    private String toJson(Reservation r) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "reservationId", r.getId().toString(),
                    "sku", r.getSku(),
                    "qty", r.getQty(),
                    "status", r.getStatus().name(),
                    "expiresAt", r.getExpiresAt().toString()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record ReservationResult(Reservation reservation, Integer replayStatus, String replayBody) {
        static ReservationResult created(Reservation r) { return new ReservationResult(r, null, null); }
        static ReservationResult replay(int status, String body) { return new ReservationResult(null, status, body); }
        public boolean isReplay() { return replayBody != null; }
    }
}
