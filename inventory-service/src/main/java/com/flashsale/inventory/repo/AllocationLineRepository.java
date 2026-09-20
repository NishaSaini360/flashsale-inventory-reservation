package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.AllocationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/** Reached only via an already tenant-scoped Allocation, so no tenant column here. */
public interface AllocationLineRepository extends JpaRepository<AllocationLine, UUID> {
    List<AllocationLine> findByAllocationId(UUID allocationId);
}
