package com.flashsale.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT, CONFIRMED,
    CANCELLED_PAYMENT_FAILED, CANCELLED_REFUNDED, CANCELLED_EXPIRED
}
