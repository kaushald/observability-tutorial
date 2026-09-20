package com.kaushaldalvi.o11y.orders.web;

import com.kaushaldalvi.o11y.orders.dto.ErrorResponse;
import com.kaushaldalvi.o11y.orders.dto.OrderRequest;
import com.kaushaldalvi.o11y.orders.dto.OrderResponse;
import com.kaushaldalvi.o11y.orders.exception.DownstreamFailureException;
import com.kaushaldalvi.o11y.orders.exception.OrderValidationException;
import com.kaushaldalvi.o11y.orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/orders/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return orderService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(OrderValidationException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DownstreamFailureException.class)
    public ResponseEntity<OrderResponse> handleDownstreamFailure(DownstreamFailureException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(OrderResponse.failed(e.getOrderId()));
    }
}
