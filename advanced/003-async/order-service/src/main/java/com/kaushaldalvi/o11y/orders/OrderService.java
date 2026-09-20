package com.kaushaldalvi.o11y.orders;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
        // Lesson 002: attributes on the current span (the server span the agent created for
        // this request), each set as soon as its value is known. They make traces queryable:
        // group failed orders by restaurant.id in Honeycomb.
        Span.current().setAttribute("restaurant.id", restaurant.getId());

        List<MenuItem> items = menuItemRepository.findAllById(request.itemIds());
        Span.current().setAttribute("order.item_count", items.size());
        int totalCents = calculatePriceCents(items);
        Span.current().setAttribute("order.total_cents", totalCents);

        CustomerOrder order =
                new CustomerOrder(restaurant.getId(), totalCents, CustomerOrder.OrderStatus.RECEIVED, Instant.now());
        order = orderRepository.save(order);
        Span.current().setAttribute("order.id", order.getId());
        log.info("Order {} received for restaurant {}", order.getId(), restaurant.getId());

        boolean approved = paymentService.authorize(request.cardNumber(), totalCents);
        if (!approved) {
            // Lesson 002: a declined card is a business outcome, not a system error.
            // Marking it as an error status would pollute every error-rate query, so it
            // is recorded as an attribute instead and the span status is left alone.
            Span.current().setAttribute("payment.declined", true);
            order.setStatus(CustomerOrder.OrderStatus.DECLINED);
            orderRepository.save(order);
            log.info("Order {} payment declined", order.getId());
            return Api.OrderResponse.declined(order.getId());
        }
        // Lesson 002: record the same attribute on an approved payment, so it is always
        // present on the order's span, not only when the card is declined.
        Span.current().setAttribute("payment.declined", false);

        try {
            fulfilOrder(order, restaurant, items);
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

    // Lesson 002: manual span with the tracer API, rather than @WithSpan, because this
    // code owns the span's whole lifecycle: it starts it, puts it on the context with
    // makeCurrent() in try-with-resources, ends it in finally, and sets its status.
    private void fulfilOrder(CustomerOrder order, Restaurant restaurant, List<MenuItem> items) {
        Tracer tracer = GlobalOpenTelemetry.getTracer("order-service");
        Span span = tracer.spanBuilder("fulfil-order").startSpan();
        try (Scope scope = span.makeCurrent()) {
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
        } catch (RestClientException e) {
            // Lesson 002: this is a real system failure (kitchen or delivery is
            // unreachable or too slow), so the span gets an ERROR status before the
            // exception is rethrown for the caller to handle.
            span.setStatus(StatusCode.ERROR, "downstream call failed");
            throw e;
        } finally {
            span.end();
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

    // Lesson 002: manual span with the annotation. The javaagent instruments this method
    // by bytecode, so calling it from another method in the same class still produces a
    // span. A Spring AOP proxy would not see that call.
    @WithSpan("calculate-price")
    private int calculatePriceCents(List<MenuItem> items) {
        return items.stream().mapToInt(MenuItem::getPriceCents).sum();
    }

    // Lesson 002: manual span with the annotation (see calculatePriceCents above).
    @WithSpan("validate-order")
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
