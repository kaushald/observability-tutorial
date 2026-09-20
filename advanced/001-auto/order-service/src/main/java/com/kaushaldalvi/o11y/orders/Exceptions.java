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
}
