package com.flashsale.order.api;

import com.flashsale.order.api.dto.Dtos.*;
import com.flashsale.order.domain.Order;
import com.flashsale.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    /** 202 — payment is asynchronous; the order is not final yet. */
    @PostMapping
    public ResponseEntity<OrderView> create(@Valid @RequestBody CreateOrderRequest req) {
        Order order = service.create(req.reservationId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(order));
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) { return view(service.get(id)); }

    static OrderView view(Order o) {
        return new OrderView(o.getId(), o.getReservationId(), o.getSku(), o.getQty(),
                o.getStatus().name(), o.getReservationExpiresAt(), o.getFailureReason());
    }
}
