package com.flashsale.commons.error;

import org.springframework.http.HttpStatus;

public final class Problems {

    public static final String BASE = "https://flashsale.example.com/problems/";

    private Problems() {}

    public static DomainException insufficientStock(String sku, int requested, int available) {
        return new DomainException(HttpStatus.CONFLICT, BASE + "insufficient-stock",
                "Not enough stock for SKU " + sku)
                .with("sku", sku).with("requested", requested).with("available", available);
    }

    public static DomainException notFound(String what, Object id) {
        return new DomainException(HttpStatus.NOT_FOUND, BASE + "not-found",
                what + " not found: " + id);
    }

    public static DomainException crossTenant(String what, Object id) {
        return new DomainException(HttpStatus.FORBIDDEN, BASE + "cross-tenant",
                "Access denied to " + what + " " + id);
    }

    public static DomainException reservationExpired(Object id) {
        return new DomainException(HttpStatus.CONFLICT, BASE + "reservation-expired",
                "Reservation " + id + " has expired");
    }

    public static DomainException idempotencyConflict(String key) {
        return new DomainException(HttpStatus.CONFLICT, BASE + "idempotency-conflict",
                "A request with Idempotency-Key " + key + " is already in progress");
    }

    public static DomainException idempotencyMismatch(String key) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, BASE + "idempotency-mismatch",
                "Idempotency-Key " + key + " was used with a different payload");
    }

    public static DomainException upstreamUnavailable(String service) {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, BASE + "upstream-unavailable",
                service + " is unavailable");
    }

    public static DomainException invalidState(String detail) {
        return new DomainException(HttpStatus.CONFLICT, BASE + "invalid-state", detail);
    }
}
