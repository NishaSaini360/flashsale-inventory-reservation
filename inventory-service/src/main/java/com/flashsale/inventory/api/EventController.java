package com.flashsale.inventory.api;

import com.flashsale.inventory.api.dto.Dtos.EventView;
import com.flashsale.inventory.repo.DomainEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final DomainEventRepository repo;

    public EventController(DomainEventRepository repo) { this.repo = repo; }

    @GetMapping
    public List<EventView> list() {
        return repo.findTop200ByOrderByIdDesc().stream()
                .map(e -> new EventView(e.getId(), e.getType(), e.getAggregateId(),
                        e.getPayload(), e.getOccurredAt()))
                .toList();
    }
}
