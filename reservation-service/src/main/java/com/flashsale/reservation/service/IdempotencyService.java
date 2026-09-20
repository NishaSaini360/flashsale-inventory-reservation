package com.flashsale.reservation.service;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.reservation.domain.IdempotencyRecord;
import com.flashsale.reservation.repo.IdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repo;

    public IdempotencyService(IdempotencyRepository repo) { this.repo = repo; }

    public String hash(String payload) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * REQUIRES_NEW so the claim commits immediately. If the caller's transaction later
     * rolls back, the claim survives — that's deliberate, it's what stops a concurrent
     * duplicate from slipping through the gap.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String idemKey, String requestHash) {
        return repo.tryClaim(TenantContext.require(), idemKey, requestHash) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(String idemKey) {
        return repo.findByTenantIdAndIdemKey(TenantContext.require(), idemKey);
    }

    /** Different body, same key → 422, per the IETF idempotency-key draft. */
    public void assertSamePayload(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw Problems.idempotencyMismatch(record.getIdemKey());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idemKey, UUID reservationId, int status, String body) {
        repo.findByTenantIdAndIdemKey(TenantContext.require(), idemKey)
            .ifPresent(r -> { r.complete(reservationId, status, body); repo.save(r); });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String idemKey) {
        repo.findByTenantIdAndIdemKey(TenantContext.require(), idemKey)
            .ifPresent(r -> { r.fail(); repo.save(r); });
    }
}
