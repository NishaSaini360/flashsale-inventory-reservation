package com.flashsale.order.service;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.order.client.InventoryClient;
import com.flashsale.order.client.ReservationClient;
import com.flashsale.order.domain.*;
import com.flashsale.order.event.EventPublisher;
import com.flashsale.order.payment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService implements PaymentCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ReservationClient reservations;
    private final InventoryClient inventory;
    private final PaymentGateway payments;
    private final OrderTxOps tx;
    private final Clock clock;

    public OrderService(ReservationClient reservations, InventoryClient inventory,
                        PaymentGateway payments, OrderTxOps tx, Clock clock) {
        this.reservations = reservations; this.inventory = inventory;
        this.payments = payments; this.tx = tx; this.clock = clock;
    }

    public Order create(UUID reservationId) {
        var view = reservations.get(reservationId);

        if (!"ACTIVE".equals(view.status())) {
            throw Problems.invalidState("Reservation " + reservationId + " is " + view.status());
        }
        if (view.expiresAt() == null || !view.expiresAt().isAfter(clock.instant())) {
            throw Problems.reservationExpired(reservationId);
        }

        var created = tx.createPending(reservationId, view.sku(), view.qty(),
                view.expiresAt(), view.allocationRef());

        // Fire and forget: the callback lands on a scheduler thread 0–5s later.
        payments.authorize(created.externalRef(), TenantContext.require(),
                created.order().getQty() * 1000L);

        return created.order();
    }

    /**
     * Called from the fake gateway's scheduler thread and from the HTTP callback.
     * Idempotent via the guarded settle: a duplicate callback does nothing.
     */
    @Override
    public void handleCallback(String externalRef, String tenantId, PaymentOutcome outcome) {
        try {
            TenantContext.set(tenantId);

            PaymentStatus next = outcome == PaymentOutcome.SUCCEEDED
                    ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;

            if (!tx.settlePayment(externalRef, next)) {
                log.info("Duplicate or late callback ignored ref={}", externalRef);
                return;
            }

            var attempt = tx.loadPayment(externalRef);
            var order = tx.load(attempt.getOrderId());

            if (outcome == PaymentOutcome.FAILED) {
                onPaymentFailed(order, tenantId, externalRef);
            } else {
                onPaymentSucceeded(order, tenantId, externalRef);
            }
        } catch (Exception e) {
            log.error("Callback processing failed ref={}", externalRef, e);
        } finally {
            TenantContext.clear();
        }
    }

    private void onPaymentFailed(Order order, String tenantId, String externalRef) {
        tx.publish(EventPublisher.PAYMENT_FAILED, order.getId().toString(),
                Map.of("externalRef", externalRef));
        reservations.cancel(order.getReservationId());   // → release at inventory
        tx.transition(order.getId(), tenantId, OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELLED_PAYMENT_FAILED, "payment failed");
        tx.publish(EventPublisher.ORDER_CANCELLED, order.getId().toString(),
                Map.of("reason", "payment failed"));
        log.info("Order {} cancelled: payment failed", order.getId());
    }

    private void onPaymentSucceeded(Order order, String tenantId, String externalRef) {
        tx.publish(EventPublisher.PAYMENT_SUCCEEDED, order.getId().toString(),
                Map.of("externalRef", externalRef));

        if (reservations.confirm(order.getReservationId())) {
            inventory.commit(order.getAllocationRef());
            tx.transition(order.getId(), tenantId, OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CONFIRMED, null);
            tx.publish(EventPublisher.ORDER_CONFIRMED, order.getId().toString(),
                    Map.of("sku", order.getSku(), "qty", order.getQty()));
            log.info("Order {} confirmed", order.getId());
            return;
        }

        // LATE PAYMENT. The hold is gone and its stock was released to other buyers.
        // We never commit an allocation that no longer exists. Try to re-acquire;
        // refund if the stock has genuinely sold out.
        log.warn("Late payment for order {} — reservation expired, compensating", order.getId());
        String compensationRef = "order-" + order.getId();
        Instant leaseUntil = clock.instant().plus(Duration.ofMinutes(5));

        if (inventory.tryAllocate(compensationRef, order.getSku(), order.getQty(), leaseUntil)) {
            inventory.commit(compensationRef);
            tx.transition(order.getId(), tenantId, OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CONFIRMED, "late payment, stock re-acquired");
            tx.publish(EventPublisher.ORDER_CONFIRMED, order.getId().toString(),
                    Map.of("sku", order.getSku(), "qty", order.getQty(), "late", true));
            log.info("Order {} confirmed via compensation", order.getId());
        } else {
            payments.refund(externalRef, tenantId);
            tx.markRefunded(externalRef);
            tx.transition(order.getId(), tenantId, OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CANCELLED_REFUNDED, "reservation expired, stock unavailable");
            tx.publish(EventPublisher.PAYMENT_REFUNDED, order.getId().toString(),
                    Map.of("externalRef", externalRef));
            tx.publish(EventPublisher.ORDER_CANCELLED, order.getId().toString(),
                    Map.of("reason", "late payment, no stock"));
            log.warn("Order {} refunded: late payment and no stock left", order.getId());
        }
    }

    public Order get(UUID id) {
        return tx.load(id);
    }
}
