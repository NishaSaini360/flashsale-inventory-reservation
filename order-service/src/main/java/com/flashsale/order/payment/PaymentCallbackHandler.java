package com.flashsale.order.payment;

public interface PaymentCallbackHandler {
    void handleCallback(String externalRef, String tenantId, PaymentOutcome outcome);
}
