package com.flashsale.reservation.repo;

import com.flashsale.reservation.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByTenantIdAndIdemKey(String tenantId, String idemKey);

    /**
     * Insert-first claim. ON CONFLICT DO NOTHING returns 0 rows if another request
     * already holds this key, which tells us to replay rather than re-execute.
     * Atomic — no check-then-act race.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO idempotency_records (tenant_id, idem_key, request_hash, state)
            VALUES (:tenantId, :idemKey, :requestHash, 'IN_PROGRESS')
            ON CONFLICT (tenant_id, idem_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(@Param("tenantId") String tenantId,
                 @Param("idemKey") String idemKey,
                 @Param("requestHash") String requestHash);
}
