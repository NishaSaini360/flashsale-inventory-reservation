package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    Optional<Allocation> findByAllocationRef(String allocationRef);

    /**
     * Guarded status transition. Returns 1 only if this caller won the race.
     * Double-release is the sneakiest oversell path there is: two releases would
     * decrement `reserved` twice and inflate availability. Only the winner of this
     * update is allowed to touch stock_items.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE allocations
               SET status = 'RELEASED', released_at = :now
             WHERE id = :id AND tenant_id = :tenantId AND status = 'ALLOCATED'
            """, nativeQuery = true)
    int markReleased(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE allocations
               SET status = 'COMMITTED', committed_at = :now
             WHERE id = :id AND tenant_id = :tenantId AND status = 'ALLOCATED'
            """, nativeQuery = true)
    int markCommitted(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("now") Instant now);

    /** Leak sweeper: allocations whose lease expired but were never released. */
    @Query(value = """
            SELECT * FROM allocations
             WHERE status = 'ALLOCATED' AND expires_at IS NOT NULL AND expires_at <= :now
             ORDER BY expires_at LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Allocation> claimExpired(@Param("now") Instant now, @Param("limit") int limit);
}
