package com.kaushaldalvi.o11y.orders.dto;

import java.util.List;

public record OrderRequest(Long restaurantId, List<Long> itemIds) {
}
