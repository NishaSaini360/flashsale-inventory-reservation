package com.flashsale.reservation.repo;

import com.flashsale.reservation.domain.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {
    List<DomainEvent> findTop200ByOrderByIdDesc();
}
