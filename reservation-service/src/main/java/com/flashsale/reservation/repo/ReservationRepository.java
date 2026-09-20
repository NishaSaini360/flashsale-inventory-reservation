package com.flashsale.reservation.repo;

import com.flashsale.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByAllocationRef(String allocationRef);

    /**
     * Expiry worker's claim. FOR UPDATE SKIP LOCKED means N instances can run this
     * concurrently with zero coordination — no ShedLock needed. Each row is claimed
     * by exactly one worker.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
               SET status = 'EXPIRED', release_state = 'PENDING', updated_at = :now
             WHERE id IN (
               SELECT id FROM reservations
                WHERE status = 'ACTIVE' AND expires_at <= :now
                ORDER BY expires_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int claimExpired(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM reservations
             WHERE status IN ('EXPIRED','CANCELLED') AND release_state = 'PENDING'
             ORDER BY updated_at LIMIT :batchSize
            """, nativeQuery = true)
    List<Reservation> findPendingReleases(@Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE reservations SET release_state = 'DONE', updated_at = :now WHERE id = :id",
           nativeQuery = true)
    int markReleaseDone(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * THE atomic gate. Confirm only succeeds if the reservation is still ACTIVE and
     * not yet past its deadline — closing the window between expires_at passing and
     * the worker actually running. Expiry-on-read alone would be wrong; this is the
     * belt to the worker's braces.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
               SET status = 'CONFIRMED', updated_at = :now
             WHERE id = :id AND tenant_id = :tenantId
               AND status = 'ACTIVE' AND expires_at > :now
            """, nativeQuery = true)
    int tryConfirm(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE reservations
               SET status = 'CANCELLED', release_state = 'PENDING', updated_at = :now
             WHERE id = :id AND tenant_id = :tenantId AND status IN ('ACTIVE','PENDING')
            """, nativeQuery = true)
    int tryCancel(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("now") Instant now);

    /** Crash recovery: PENDING reservations stuck because the process died mid-allocate. */
    @Query(value = """
            SELECT * FROM reservations
             WHERE status = 'PENDING' AND created_at < :cutoff
             ORDER BY created_at LIMIT :batchSize
            """, nativeQuery = true)
    List<Reservation> findStalePending(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
