package com.kaushaldalvi.o11y.orders.exception;

public class DownstreamFailureException extends RuntimeException {

    private final Long orderId;

    public DownstreamFailureException(Long orderId) {
        super("Order " + orderId + " failed");
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
