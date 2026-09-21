package com.flashsale.order.repo;

import com.flashsale.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndTenantId(UUID id, String tenantId);

    /** Guarded transition — only the caller that wins may act on it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders SET status = :next, failure_reason = :reason, updated_at = :now
             WHERE id = :id AND tenant_id = :tenantId AND status = :expected
            """, nativeQuery = true)
    int transition(@Param("id") UUID id, @Param("tenantId") String tenantId,
                   @Param("expected") String expected, @Param("next") String next,
                   @Param("reason") String reason, @Param("now") Instant now);

    @Query(value = """
            SELECT * FROM orders
             WHERE status = 'PENDING_PAYMENT' AND created_at < :cutoff
             ORDER BY created_at LIMIT :batchSize
            """, nativeQuery = true)
    List<Order> findStuck(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
