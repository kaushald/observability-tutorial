package com.kaushaldalvi.o11y.orders;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Both REST controllers, nested in one file so the lesson copies of this service stay
 * to a handful of files.
 */
public final class Controllers {

    private Controllers() {
    }

    @RestController
    public static class OrderController {

        private final OrderService orderService;

        public OrderController(OrderService orderService) {
            this.orderService = orderService;
        }

        @PostMapping("/api/orders")
        public ResponseEntity<Api.OrderResponse> placeOrder(@RequestBody Api.OrderRequest request) {
            Api.OrderResponse response = orderService.placeOrder(request);
            HttpStatus status =
                    "DECLINED".equals(response.status()) ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(response);
        }

        @GetMapping("/api/orders/{id}")
        public ResponseEntity<Api.OrderResponse> getOrder(@PathVariable Long id) {
            return orderService.getOrder(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        // Lesson 005: an unknown order falls through to the same not-found handling as
        // getOrder above; a known order in the wrong status is a 409 via the exception
        // handler below.
        @PostMapping("/api/orders/{id}/refund")
        public ResponseEntity<Api.RefundResponse> refundOrder(@PathVariable Long id) {
            return orderService.refundOrder(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @ExceptionHandler(Exceptions.OrderValidationException.class)
        public ResponseEntity<Api.ErrorResponse> handleValidation(Exceptions.OrderValidationException e) {
            return ResponseEntity.badRequest().body(new Api.ErrorResponse(e.getMessage()));
        }

        @ExceptionHandler(Exceptions.DownstreamFailureException.class)
        public ResponseEntity<Api.OrderResponse> handleDownstreamFailure(Exceptions.DownstreamFailureException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Api.OrderResponse.failed(e.getOrderId()));
        }

        // Lesson 005: only a FAILED order can be refunded.
        @ExceptionHandler(Exceptions.OrderNotRefundableException.class)
        public ResponseEntity<Api.ErrorResponse> handleOrderNotRefundable(Exceptions.OrderNotRefundableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new Api.ErrorResponse("Only failed orders can be refunded"));
        }
    }

    @RestController
    public static class RestaurantController {

        private final RestaurantRepository restaurantRepository;
        private final MenuItemRepository menuItemRepository;

        public RestaurantController(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository) {
            this.restaurantRepository = restaurantRepository;
            this.menuItemRepository = menuItemRepository;
        }

        @GetMapping("/api/restaurants")
        public List<Api.RestaurantResponse> listRestaurants() {
            return restaurantRepository.findAll().stream()
                    .map(r -> new Api.RestaurantResponse(r.getId(), r.getName(), r.getCuisine()))
                    .toList();
        }

        @GetMapping("/api/restaurants/{id}/menu")
        @Transactional(readOnly = true)
        public ResponseEntity<List<Api.MenuItemResponse>> menu(@PathVariable Long id) {
            if (restaurantRepository.findById(id).isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            List<MenuItem> items = menuItemRepository.findByRestaurantId(id);
            // Deliberate N+1: items are loaded with a plain derived query, then tags are
            // read one item at a time inside this read-only transaction. Tags are a lazy
            // element collection with no join fetch, entity graph or batch size, so each
            // getTags() call issues its own SELECT. Lesson 006 fixes this from traces.
            List<Api.MenuItemResponse> menu = items.stream()
                    .map(item -> new Api.MenuItemResponse(
                            item.getId(),
                            item.getName(),
                            item.getPriceCents(),
                            item.getTags().stream().sorted().toList()))
                    .toList();
            return ResponseEntity.ok(menu);
        }
    }
}
