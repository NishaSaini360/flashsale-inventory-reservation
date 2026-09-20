package com.flashsale.inventory.service;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.domain.Allocation;
import com.flashsale.inventory.repo.AllocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Self-healing. If reservation-service crashes between "allocate succeeded" and
 * "reservation saved", the hold would leak forever. Every allocation carries a lease
 * (expires_at = reservation TTL + grace); this releases anything past it.
 *
 * Inventory protects its own invariant without knowing reservation-service exists.
 */
@Component
public class AllocationSweeper {

    private static final Logger log = LoggerFactory.getLogger(AllocationSweeper.class);

    private final AllocationRepository allocations;
    private final AllocationService service;
    private final Clock clock;

    public AllocationSweeper(AllocationRepository allocations, AllocationService service, Clock clock) {
        this.allocations = allocations; this.service = service; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.sweeper.interval-ms:10000}")
    @Transactional
    public void sweep() {
        List<Allocation> expired = allocations.claimExpired(clock.instant(), 100);
        for (Allocation a : expired) {
            try {
                TenantContext.set(a.getTenantId());
                service.release(a.getAllocationRef());
                log.warn("Swept leaked allocation ref={} tenant={}", a.getAllocationRef(), a.getTenantId());
            } catch (Exception e) {
                log.error("Sweep failed for ref={}", a.getAllocationRef(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
