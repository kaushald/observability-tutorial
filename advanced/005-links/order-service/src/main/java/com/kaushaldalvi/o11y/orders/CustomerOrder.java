package com.kaushaldalvi.o11y.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    public enum OrderStatus {
        RECEIVED,
        CONFIRMED,
        DECLINED,
        FAILED,
        // Lesson 005: only a FAILED order can move here, via POST /api/orders/{id}/refund.
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long restaurantId;

    private int totalCents;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String driver;

    private Integer etaMinutes;

    private Instant createdAt;

    // Lesson 005: the trace and span ID of the request that placed this order, captured
    // while that span was current. A later, unrelated request (the refund) reads these
    // back and links its own span to that one instead of becoming its child.
    private String traceId;

    private String spanId;

    protected CustomerOrder() {
        // for JPA
    }

    public CustomerOrder(Long restaurantId, int totalCents, OrderStatus status, Instant createdAt) {
        this.restaurantId = restaurantId;
        this.totalCents = totalCents;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public int getTotalCents() {
        return totalCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }
}
