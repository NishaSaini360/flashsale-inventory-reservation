package com.flashsale.order.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates an async gateway: schedules a callback 0–5s later on a separate thread,
 * never blocking the request thread.
 *
 * `app.payment.mode` makes every branch deterministically testable:
 *   RANDOM | ALWAYS_SUCCESS | ALWAYS_FAIL | DELAYED (fires past a short TTL, to
 *   exercise the late-payment compensation path).
 */
@Component
public class FakePaymentAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentAdapter.class);

    private final TaskScheduler scheduler;
    private final PaymentCallbackHandler handler;
    private final String mode;
    private final long minDelayMs;
    private final long maxDelayMs;
    private final double successRate;

    public FakePaymentAdapter(TaskScheduler scheduler,
                              @Lazy PaymentCallbackHandler handler,
                              @Value("${app.payment.mode}") String mode,
                              @Value("${app.payment.min-delay-ms}") long minDelayMs,
                              @Value("${app.payment.max-delay-ms}") long maxDelayMs,
                              @Value("${app.payment.success-rate}") double successRate) {
        this.scheduler = scheduler; this.handler = handler; this.mode = mode;
        this.minDelayMs = minDelayMs; this.maxDelayMs = maxDelayMs; this.successRate = successRate;
    }

    @Override
    public void authorize(String externalRef, String tenantId, long amountMinor) {
        long delay = switch (mode) {
            case "DELAYED" -> 15_000;
            default -> ThreadLocalRandom.current().nextLong(minDelayMs, Math.max(maxDelayMs, minDelayMs + 1));
        };

        PaymentOutcome outcome = switch (mode) {
            case "ALWAYS_SUCCESS", "DELAYED" -> PaymentOutcome.SUCCEEDED;
            case "ALWAYS_FAIL" -> PaymentOutcome.FAILED;
            default -> ThreadLocalRandom.current().nextDouble() < successRate
                    ? PaymentOutcome.SUCCEEDED : PaymentOutcome.FAILED;
        };

        log.info("Fake payment scheduled ref={} outcome={} delayMs={}", externalRef, outcome, delay);
        scheduler.schedule(
                () -> handler.handleCallback(externalRef, tenantId, outcome),
                Instant.now().plusMillis(delay));
    }

    @Override
    public void refund(String externalRef, String tenantId) {
        log.warn("Fake refund issued ref={} tenant={}", externalRef, tenantId);
    }
}
