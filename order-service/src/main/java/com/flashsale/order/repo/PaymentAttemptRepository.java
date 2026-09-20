package com.flashsale.order.repo;

import com.flashsale.order.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByExternalRef(String externalRef);

    /**
     * Callback idempotency. A gateway delivering the same callback twice only moves
     * the row once; the loser sees 0 rows and returns a no-op 200.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempts SET status = :next, settled_at = :now
             WHERE external_ref = :ref AND status = 'PENDING'
            """, nativeQuery = true)
    int settle(@Param("ref") String ref, @Param("next") String next, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempts SET status = 'REFUNDED', settled_at = :now
             WHERE external_ref = :ref AND status = 'SUCCEEDED'
            """, nativeQuery = true)
    int refund(@Param("ref") String ref, @Param("now") Instant now);
}
