package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {
    List<DomainEvent> findTop200ByTenantIdOrderByIdDesc(String tenantId);
}
