package com.flashsale.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.domain.DomainEvent;
import com.flashsale.order.repo.DomainEventRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventPublisher {

    public static final String ORDER_CREATED     = "OrderCreated";
    public static final String ORDER_CONFIRMED   = "OrderConfirmed";
    public static final String ORDER_CANCELLED   = "OrderCancelled";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED    = "PaymentFailed";
    public static final String PAYMENT_REFUNDED  = "PaymentRefunded";

    private final DomainEventRepository repo;
    private final ObjectMapper mapper;

    public EventPublisher(DomainEventRepository repo, ObjectMapper mapper) {
        this.repo = repo; this.mapper = mapper;
    }

    public void publish(String type, String aggregateId, Map<String, Object> payload) {
        try {
            repo.save(new DomainEvent(type, "Order", aggregateId, mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
