package com.kaushaldalvi.o11y.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long orderId,
        String status,
        Integer totalCents,
        String driver,
        Integer etaMinutes,
        String message) {

    public static OrderResponse confirmed(Long orderId, int totalCents, String driver, int etaMinutes) {
        return new OrderResponse(orderId, "CONFIRMED", totalCents, driver, etaMinutes, null);
    }

    public static OrderResponse failed(Long orderId) {
        return new OrderResponse(orderId, "FAILED", null, null, null, "Order could not be completed");
    }

    public static OrderResponse of(Long orderId, String status, int totalCents, String driver, Integer etaMinutes) {
        return new OrderResponse(orderId, status, totalCents, driver, etaMinutes, null);
    }
}
