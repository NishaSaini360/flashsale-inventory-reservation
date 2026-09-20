package com.flashsale.reservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.reservation.domain.DomainEvent;
import com.flashsale.reservation.repo.DomainEventRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventPublisher {

    public static final String RESERVATION_CREATED   = "ReservationCreated";
    public static final String RESERVATION_EXPIRED   = "ReservationExpired";
    public static final String RESERVATION_CONFIRMED = "ReservationConfirmed";
    public static final String RESERVATION_CANCELLED = "ReservationCancelled";

    private final DomainEventRepository repo;
    private final ObjectMapper mapper;

    public EventPublisher(DomainEventRepository repo, ObjectMapper mapper) {
        this.repo = repo; this.mapper = mapper;
    }

    public void publish(String type, String aggregateId, Map<String, Object> payload) {
        try {
            repo.save(new DomainEvent(type, "Reservation", aggregateId, mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
