package com.flashsale.reservation.api;

import com.flashsale.reservation.api.dto.Dtos.EventView;
import com.flashsale.reservation.repo.DomainEventRepository;
import com.flashsale.commons.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final DomainEventRepository repo;

    public EventController(DomainEventRepository repo) { this.repo = repo; }

    @GetMapping
    public List<EventView> list() {
        return repo.findTop200ByTenantIdOrderByIdDesc(TenantContext.require()).stream()
                .map(e -> new EventView(e.getId(), e.getType(), e.getAggregateId(),
                        e.getPayload(), e.getOccurredAt()))
                .toList();
    }
}
