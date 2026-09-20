package com.flashsale.inventory.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.inventory.domain.DomainEvent;
import com.flashsale.inventory.repo.DomainEventRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Events are written in the SAME transaction as the state change — no dual-write problem. */
@Component
public class EventPublisher {

    public static final String STOCK_RESERVED  = "StockReserved";
    public static final String STOCK_RELEASED  = "StockReleased";
    public static final String STOCK_COMMITTED = "StockCommitted";

    private final DomainEventRepository repo;
    private final ObjectMapper mapper;

    public EventPublisher(DomainEventRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void publish(String type, String aggregateType, String aggregateId, Map<String, Object> payload) {
        try {
            repo.save(new DomainEvent(type, aggregateType, aggregateId, mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
