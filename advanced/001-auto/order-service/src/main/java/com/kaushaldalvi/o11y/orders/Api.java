package com.kaushaldalvi.o11y.orders;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * All request/response contracts for this service, nested in one class so the lesson
 * copies of this service stay to a handful of files.
 */
public final class Api {

    private Api() {
    }

    public record OrderRequest(Long restaurantId, List<Long> itemIds, String cardNumber) {
    }

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

        public static OrderResponse declined(Long orderId) {
            return new OrderResponse(orderId, "DECLINED", null, null, null, "Payment declined");
        }

        public static OrderResponse of(Long orderId, String status, int totalCents, String driver, Integer etaMinutes) {
            return new OrderResponse(orderId, status, totalCents, driver, etaMinutes, null);
        }
    }

    public record RestaurantResponse(Long id, String name, String cuisine) {
    }

    public record MenuItemResponse(Long id, String name, int priceCents, List<String> tags) {
    }

    public record ErrorResponse(String message) {
    }

    public record ConfirmationRequest(Long orderId, String message) {
    }
}
