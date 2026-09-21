package com.flashsale.order.service;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.order.domain.*;
import com.flashsale.order.event.EventPublisher;
import com.flashsale.order.repo.OrderRepository;
import com.flashsale.order.repo.PaymentAttemptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Separate bean so @Transactional applies — self-invocation bypasses the proxy. */
@Component
public class OrderTxOps {

    private final OrderRepository orders;
    private final PaymentAttemptRepository payments;
    private final EventPublisher events;
    private final Clock clock;

    public OrderTxOps(OrderRepository orders, PaymentAttemptRepository payments,
                      EventPublisher events, Clock clock) {
        this.orders = orders; this.payments = payments; this.events = events; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreatedOrder createPending(UUID reservationId, String sku, int qty,
                                      Instant reservationExpiresAt, String allocationRef) {
        Order order = orders.save(new Order(reservationId, sku, qty, reservationExpiresAt, allocationRef));
        String externalRef = "pay-" + order.getId();
        payments.save(new PaymentAttempt(order.getId(), externalRef, qty * 1000L));
        events.publish(EventPublisher.ORDER_CREATED, order.getId().toString(),
                Map.of("sku", sku, "qty", qty, "reservationId", reservationId.toString()));
        return new CreatedOrder(order, externalRef);
    }

    /** Returns true only if this caller won the guarded transition. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settlePayment(String externalRef, PaymentStatus next) {
        return payments.settle(externalRef, next.name(), clock.instant()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transition(UUID orderId, String tenantId, OrderStatus expected,
                              OrderStatus next, String reason) {
        return orders.transition(orderId, tenantId, expected.name(), next.name(),
                reason, clock.instant()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefunded(String externalRef) {
        payments.refund(externalRef, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String type, String aggregateId, Map<String, Object> payload) {
        events.publish(type, aggregateId, payload);
    }

    @Transactional(readOnly = true)
    public Order load(UUID orderId) {
        String tenantId = TenantContext.require();
        return orders.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> Problems.crossTenant("Order", orderId));
    }

    @Transactional(readOnly = true)
    public PaymentAttempt loadPayment(String externalRef) {
        return payments.findByExternalRef(externalRef).orElseThrow();
    }

    public record CreatedOrder(Order order, String externalRef) {}
}
