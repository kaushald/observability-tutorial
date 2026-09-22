package com.kaushaldalvi.o11y.orders;

/**
 * The two error types this service throws, nested in one small file.
 */
public final class Exceptions {

    private Exceptions() {
    }

    public static class OrderValidationException extends RuntimeException {

        public OrderValidationException(String message) {
            super(message);
        }
    }

    public static class DownstreamFailureException extends RuntimeException {

        private final Long orderId;

        public DownstreamFailureException(Long orderId) {
            super("Order " + orderId + " failed");
            this.orderId = orderId;
        }

        public Long getOrderId() {
            return orderId;
        }
    }

    // Lesson 005: thrown when a refund is requested for an order that is not FAILED
    // (including one that has already been refunded), mapped to HTTP 409 by the controller.
    public static class OrderNotRefundableException extends RuntimeException {

        private final Long orderId;

        public OrderNotRefundableException(Long orderId) {
            super("Order " + orderId + " cannot be refunded");
            this.orderId = orderId;
        }

        public Long getOrderId() {
            return orderId;
        }
    }
}
