package com.flashsale.order.payment;

/** Port. The adapter is fake and lives in-process; no real gateway is called. */
public interface PaymentGateway {
    void authorize(String externalRef, String tenantId, long amountMinor);
    void refund(String externalRef, String tenantId);
}
