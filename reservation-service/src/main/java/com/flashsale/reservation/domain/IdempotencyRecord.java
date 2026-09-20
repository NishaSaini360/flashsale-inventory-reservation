package com.flashsale.reservation.domain;

import com.flashsale.commons.tenant.TenantAwareEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends TenantAwareEntity {

    public enum State { IN_PROGRESS, COMPLETED, FAILED }

    @Id @GeneratedValue private UUID id;

    @Column(name = "idem_key", nullable = false)     private String idemKey;
    @Column(name = "request_hash", nullable = false) private String requestHash;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private State state;

    @Column(name = "reservation_id")  private UUID reservationId;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "response_body")   private String responseBody;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String idemKey, String requestHash) {
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.state = State.IN_PROGRESS;
    }

    public void complete(UUID reservationId, int status, String body) {
        this.state = State.COMPLETED;
        this.reservationId = reservationId;
        this.responseStatus = status;
        this.responseBody = body;
    }

    public void fail() { this.state = State.FAILED; }

    public String getIdemKey() { return idemKey; }
    public String getRequestHash() { return requestHash; }
    public State getState() { return state; }
    public UUID getReservationId() { return reservationId; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
}
