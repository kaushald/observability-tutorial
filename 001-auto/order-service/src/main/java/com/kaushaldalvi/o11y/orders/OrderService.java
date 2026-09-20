package com.kaushaldalvi.o11y.orders;

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
    private final PaymentService paymentService;
    private final ConfirmationSender confirmationSender;
    private final RestClient kitchenRestClient;
    private final RestClient deliveryRestClient;

    public OrderService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            CustomerOrderRepository orderRepository,
            PaymentService paymentService,
            ConfirmationSender confirmationSender,
            @Qualifier("kitchenRestClient") RestClient kitchenRestClient,
            @Qualifier("deliveryRestClient") RestClient deliveryRestClient) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.confirmationSender = confirmationSender;
        this.kitchenRestClient = kitchenRestClient;
        this.deliveryRestClient = deliveryRestClient;
    }

    public Api.OrderResponse placeOrder(Api.OrderRequest request) {
        Restaurant restaurant = validate(request);

        List<MenuItem> items = menuItemRepository.findAllById(request.itemIds());
        int totalCents = items.stream().mapToInt(MenuItem::getPriceCents).sum();

        CustomerOrder order =
                new CustomerOrder(restaurant.getId(), totalCents, CustomerOrder.OrderStatus.RECEIVED, Instant.now());
        order = orderRepository.save(order);
        log.info("Order {} received for restaurant {}", order.getId(), restaurant.getId());

        boolean approved = paymentService.authorize(request.cardNumber(), totalCents);
        if (!approved) {
            order.setStatus(CustomerOrder.OrderStatus.DECLINED);
            orderRepository.save(order);
            log.info("Order {} payment declined", order.getId());
            return Api.OrderResponse.declined(order.getId());
        }

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

            order.setStatus(CustomerOrder.OrderStatus.CONFIRMED);
            order.setDriver(assignment.driver());
            order.setEtaMinutes(assignment.etaMinutes());
            orderRepository.save(order);
            log.info("Order {} confirmed", order.getId());

            confirmationSender.sendAsync(order.getId(), order.getDriver(), order.getEtaMinutes());

            return Api.OrderResponse.confirmed(order.getId(), order.getTotalCents(), order.getDriver(), order.getEtaMinutes());
        } catch (RestClientException e) {
            order.setStatus(CustomerOrder.OrderStatus.FAILED);
            orderRepository.save(order);
            log.error("Order {} failed", order.getId());
            throw new Exceptions.DownstreamFailureException(order.getId());
        }
    }

    public Optional<Api.OrderResponse> getOrder(Long id) {
        return orderRepository.findById(id)
                .map(order -> Api.OrderResponse.of(
                        order.getId(),
                        order.getStatus().name(),
                        order.getTotalCents(),
                        order.getDriver(),
                        order.getEtaMinutes()));
    }

    private Restaurant validate(Api.OrderRequest request) {
        if (request.restaurantId() == null) {
            throw new Exceptions.OrderValidationException("restaurantId is required");
        }
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new Exceptions.OrderValidationException("Unknown restaurant " + request.restaurantId()));

        if (request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new Exceptions.OrderValidationException("itemIds must not be empty");
        }

        List<MenuItem> items = menuItemRepository.findAllById(request.itemIds());
        Set<Long> foundIds = items.stream().map(MenuItem::getId).collect(Collectors.toSet());
        if (!foundIds.containsAll(request.itemIds())) {
            throw new Exceptions.OrderValidationException("Unknown menu item in order");
        }

        boolean allBelongToRestaurant = items.stream()
                .allMatch(item -> item.getRestaurant().getId().equals(restaurant.getId()));
        if (!allBelongToRestaurant) {
            throw new Exceptions.OrderValidationException("All items must belong to restaurant " + restaurant.getId());
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
