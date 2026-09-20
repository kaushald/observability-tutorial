package com.kaushaldalvi.o11y.orders.service;

import com.kaushaldalvi.o11y.orders.domain.CustomerOrder;
import com.kaushaldalvi.o11y.orders.domain.MenuItem;
import com.kaushaldalvi.o11y.orders.domain.OrderStatus;
import com.kaushaldalvi.o11y.orders.domain.Restaurant;
import com.kaushaldalvi.o11y.orders.dto.OrderRequest;
import com.kaushaldalvi.o11y.orders.dto.OrderResponse;
import com.kaushaldalvi.o11y.orders.exception.DownstreamFailureException;
import com.kaushaldalvi.o11y.orders.exception.OrderValidationException;
import com.kaushaldalvi.o11y.orders.repository.CustomerOrderRepository;
import com.kaushaldalvi.o11y.orders.repository.MenuItemRepository;
import com.kaushaldalvi.o11y.orders.repository.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomerOrderRepository orderRepository;
    private final RestClient kitchenRestClient;
    private final RestClient deliveryRestClient;

    public OrderService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            CustomerOrderRepository orderRepository,
            @Qualifier("kitchenRestClient") RestClient kitchenRestClient,
            @Qualifier("deliveryRestClient") RestClient deliveryRestClient) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.kitchenRestClient = kitchenRestClient;
        this.deliveryRestClient = deliveryRestClient;
    }

    public OrderResponse placeOrder(OrderRequest request) {
        Restaurant restaurant = validate(request);

        List<MenuItem> items = menuItemRepository.findAllById(request.itemIds());
        int totalCents = items.stream().mapToInt(MenuItem::getPriceCents).sum();

        CustomerOrder order = new CustomerOrder(restaurant.getId(), totalCents, OrderStatus.RECEIVED, Instant.now());
        order = orderRepository.save(order);
        log.info("Order {} received for restaurant {}", order.getId(), restaurant.getId());

        try {
            TicketResponse ticket = kitchenRestClient.post()
                    .uri("/tickets")
                    .body(new TicketRequest(order.getId(), restaurant.getId(), items.size()))
                    .retrieve()
                    .body(TicketResponse.class);

            AssignmentResponse assignment = deliveryRestClient.post()
                    .uri("/assignments")
                    .body(new AssignmentRequest(order.getId(), restaurant.getId()))
                    .retrieve()
                    .body(AssignmentResponse.class);

            order.setStatus(OrderStatus.CONFIRMED);
            order.setDriver(assignment.driver());
            order.setEtaMinutes(assignment.etaMinutes());
            orderRepository.save(order);
            log.info("Order {} confirmed", order.getId());

            return OrderResponse.confirmed(order.getId(), order.getTotalCents(), order.getDriver(), order.getEtaMinutes());
        } catch (RestClientException e) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.error("Order {} failed", order.getId());
            throw new DownstreamFailureException(order.getId());
        }
    }

    public Optional<OrderResponse> getOrder(Long id) {
        return orderRepository.findById(id)
                .map(order -> OrderResponse.of(
                        order.getId(),
                        order.getStatus().name(),
                        order.getTotalCents(),
                        order.getDriver(),
                        order.getEtaMinutes()));
    }

    private Restaurant validate(OrderRequest request) {
        if (request.restaurantId() == null) {
            throw new OrderValidationException("restaurantId is required");
        }
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new OrderValidationException("Unknown restaurant " + request.restaurantId()));

        if (request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new OrderValidationException("itemIds must not be empty");
        }

        List<MenuItem> items = menuItemRepository.findAllById(request.itemIds());
        Set<Long> foundIds = items.stream().map(MenuItem::getId).collect(Collectors.toSet());
        if (!foundIds.containsAll(request.itemIds())) {
            throw new OrderValidationException("Unknown menu item in order");
        }

        boolean allBelongToRestaurant = items.stream()
                .allMatch(item -> item.getRestaurant().getId().equals(restaurant.getId()));
        if (!allBelongToRestaurant) {
            throw new OrderValidationException("All items must belong to restaurant " + restaurant.getId());
        }

        return restaurant;
    }

    private record TicketRequest(Long orderId, Long restaurantId, int itemCount) {
    }

    private record TicketResponse(String ticketId, int prepMillis) {
    }

    private record AssignmentRequest(Long orderId, Long restaurantId) {
    }

    private record AssignmentResponse(String driver, int etaMinutes) {
    }
}
