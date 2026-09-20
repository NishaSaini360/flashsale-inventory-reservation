package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    List<StockItem> findByTenantIdAndProductIdOrderByWarehouseIdAsc(String tenantId, UUID productId);

    /**
     * THE core query. A single atomic UPDATE whose predicate re-checks availability.
     *
     * Under READ COMMITTED, Postgres re-evaluates the WHERE clause against the newly
     * committed row version after acquiring the row lock (EvalPlanQual). So a concurrent
     * writer that already consumed stock makes this predicate fail rather than letting
     * this update overwrite theirs. A plain SELECT-then-UPDATE gets no such protection.
     *
     * Returns 1 on success, 0 on insufficient stock. Never throws, never oversells.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
               SET reserved = reserved + :qty, updated_at = now()
             WHERE id = :id AND tenant_id = :tenantId
               AND on_hand - reserved >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("qty") int qty);

    /** Release: hold goes away, stock becomes available again. on_hand untouched. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
               SET reserved = reserved - :qty, updated_at = now()
             WHERE id = :id AND tenant_id = :tenantId AND reserved >= :qty
            """, nativeQuery = true)
    int releaseReserved(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("qty") int qty);

    /** Commit: held stock becomes sold stock. available is UNCHANGED (both sides drop). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
               SET on_hand = on_hand - :qty, reserved = reserved - :qty, updated_at = now()
             WHERE id = :id AND tenant_id = :tenantId AND reserved >= :qty AND on_hand >= :qty
            """, nativeQuery = true)
    int commitReserved(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items SET on_hand = on_hand + :qty, updated_at = now()
             WHERE id = :id AND tenant_id = :tenantId
            """, nativeQuery = true)
    int addOnHand(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("qty") int qty);
}
