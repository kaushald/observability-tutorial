package com.kaushaldalvi.o11y.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationTests {

    private static final HttpServer KITCHEN_SERVER;
    private static final HttpServer DELIVERY_SERVER;

    private static final AtomicLong KITCHEN_DELAY_MILLIS = new AtomicLong(0);
    private static final AtomicInteger KITCHEN_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> KITCHEN_RESPONSE_BODY =
            new AtomicReference<>("{\"ticketId\":\"tk-1\",\"prepMillis\":180}");
    private static final AtomicReference<String> LAST_KITCHEN_REQUEST_BODY = new AtomicReference<>();

    private static final AtomicLong DELIVERY_DELAY_MILLIS = new AtomicLong(0);
    private static final AtomicInteger DELIVERY_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> DELIVERY_RESPONSE_BODY =
            new AtomicReference<>("{\"driver\":\"Priya\",\"etaMinutes\":25}");

    private static final AtomicInteger NOTIFICATION_STATUS = new AtomicInteger(202);
    private static final AtomicReference<String> LAST_NOTIFICATION_BODY = new AtomicReference<>();

    static {
        try {
            KITCHEN_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            KITCHEN_SERVER.createContext("/tickets", exchange -> {
                String body = readBody(exchange);
                LAST_KITCHEN_REQUEST_BODY.set(body);
                sleepFor(KITCHEN_DELAY_MILLIS.get());
                writeResponse(exchange, KITCHEN_STATUS.get(), KITCHEN_RESPONSE_BODY.get());
            });
            // HttpServer defaults to a single-threaded executor, which would serialize
            // handling across tests: a slow request in one test would block the next
            // test's fast request from even starting. Give it a real thread pool so a
            // sleeping handler from an earlier (timeout) test cannot delay a later one.
            KITCHEN_SERVER.setExecutor(Executors.newCachedThreadPool());
            KITCHEN_SERVER.start();

            DELIVERY_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            DELIVERY_SERVER.createContext("/assignments", exchange -> {
                readBody(exchange);
                sleepFor(DELIVERY_DELAY_MILLIS.get());
                writeResponse(exchange, DELIVERY_STATUS.get(), DELIVERY_RESPONSE_BODY.get());
            });
            DELIVERY_SERVER.createContext("/notifications", exchange -> {
                String body = readBody(exchange);
                LAST_NOTIFICATION_BODY.set(body);
                writeResponse(exchange, NOTIFICATION_STATUS.get(), "{}");
            });
            DELIVERY_SERVER.setExecutor(Executors.newCachedThreadPool());
            DELIVERY_SERVER.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("kitchen.url", () -> "http://localhost:" + KITCHEN_SERVER.getAddress().getPort());
        registry.add("delivery.url", () -> "http://localhost:" + DELIVERY_SERVER.getAddress().getPort());
        registry.add("downstream.read-timeout-ms", () -> "300");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @AfterAll
    static void stopServers() {
        KITCHEN_SERVER.stop(0);
        DELIVERY_SERVER.stop(0);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final TestRestTemplate rest = new TestRestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void resetStubs() {
        KITCHEN_DELAY_MILLIS.set(0);
        KITCHEN_STATUS.set(200);
        KITCHEN_RESPONSE_BODY.set("{\"ticketId\":\"tk-1\",\"prepMillis\":180}");
        LAST_KITCHEN_REQUEST_BODY.set(null);

        DELIVERY_DELAY_MILLIS.set(0);
        DELIVERY_STATUS.set(200);
        DELIVERY_RESPONSE_BODY.set("{\"driver\":\"Priya\",\"etaMinutes\":25}");

        NOTIFICATION_STATUS.set(202);
        LAST_NOTIFICATION_BODY.set(null);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void restaurantsListReturnsTheThreeSeededRestaurants() {
        ResponseEntity<Api.RestaurantResponse[]> response =
                rest.getForEntity(url("/api/restaurants"), Api.RestaurantResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Api.RestaurantResponse> restaurants = List.of(response.getBody());
        assertThat(restaurants).hasSize(3);
        assertThat(restaurants).extracting(Api.RestaurantResponse::name)
                .containsExactlyInAnyOrder("Bella Pizza", "Slow Noodles", "Taco Corner");
    }

    @Test
    void menuForRestaurant1Has8ItemsWithSortedTagsAndUnknownRestaurantGives404() {
        ResponseEntity<Api.MenuItemResponse[]> okResponse =
                rest.getForEntity(url("/api/restaurants/1/menu"), Api.MenuItemResponse[].class);
        assertThat(okResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Api.MenuItemResponse> items = List.of(okResponse.getBody());
        assertThat(items).hasSize(8);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.tags()).isNotEmpty();
            assertThat(item.tags()).isSorted();
        });

        ResponseEntity<String> notFound = rest.getForEntity(url("/api/restaurants/999/menu"), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void loadingMenuForRestaurant1ExecutesAFewPreparedStatements() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        ResponseEntity<Api.MenuItemResponse[]> response =
                rest.getForEntity(url("/api/restaurants/1/menu"), Api.MenuItemResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Lesson 006: the N+1 is fixed. The restaurant lookup and one query that fetches
        // the items together with their tags, instead of one query per item.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void happyPathOrderReturns201ConfirmedWithDriverAndCorrectTotal() throws Exception {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Api.OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("CONFIRMED");
        assertThat(body.driver()).isEqualTo("Priya");
        assertThat(body.etaMinutes()).isEqualTo(25);
        assertThat(body.totalCents()).isEqualTo(1200 + 1400);

        String kitchenBody = LAST_KITCHEN_REQUEST_BODY.get();
        assertThat(kitchenBody).isNotNull();
        JsonNode kitchenJson = mapper.readTree(kitchenBody);
        assertThat(kitchenJson.get("orderId").asLong()).isEqualTo(body.orderId());
        assertThat(kitchenJson.get("restaurantId").asLong()).isEqualTo(1L);
        assertThat(kitchenJson.get("itemCount").asInt()).isEqualTo(2);
    }

    @Test
    void slowKitchenReturns504FailedAndOrderStaysFailed() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        Api.OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("FAILED");
        assertThat(body.message()).isNotNull();

        ResponseEntity<Api.OrderResponse> lookup =
                rest.getForEntity(url("/api/orders/" + body.orderId()), Api.OrderResponse.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookup.getBody()).isNotNull();
        assertThat(lookup.getBody().status()).isEqualTo("FAILED");
    }

    @Test
    void itemsFromAnotherRestaurantGive400() {
        // Item 9 belongs to restaurant 2 (Slow Noodles); restaurant 1 owns items 1-8.
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 9L), null);

        ResponseEntity<String> response = rest.postForEntity(url("/api/orders"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("message");
    }

    @Test
    void unknownRestaurantOnOrderGives400() {
        Api.OrderRequest request = new Api.OrderRequest(999L, List.of(1L), null);

        ResponseEntity<String> response = rest.postForEntity(url("/api/orders"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingCardNumberIsApproved() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void cardNotEndingIn0000IsApproved() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), "4242 4242 4242 4242");

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void cardEndingIn0000IsDeclinedAndKitchenIsNeverCalled() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), "4242424242420000");

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        Api.OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("DECLINED");
        assertThat(body.message()).isEqualTo("Payment declined");

        ResponseEntity<Api.OrderResponse> lookup =
                rest.getForEntity(url("/api/orders/" + body.orderId()), Api.OrderResponse.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookup.getBody()).isNotNull();
        assertThat(lookup.getBody().status()).isEqualTo("DECLINED");

        assertThat(LAST_KITCHEN_REQUEST_BODY.get()).isNull();
    }

    @Test
    void cardWithSpacesAndDashesEndingIn0000IsDeclined() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), "4000-0000-0000-0000");

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DECLINED");
    }

    @Test
    void confirmationIsSentInBackgroundWithoutDelayingTheOrderResponse() throws Exception {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        long beforeMs = System.currentTimeMillis();
        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);
        long responseMs = System.currentTimeMillis();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Api.OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        Long orderId = body.orderId();
        String needle = "\"orderId\":" + orderId;

        String notificationBody = awaitNotificationContaining(needle);
        long notifiedMs = System.currentTimeMillis();

        assertThat(notificationBody).as("confirmation should reach the delivery stub within 3s").isNotNull();
        JsonNode notificationJson = mapper.readTree(notificationBody);
        assertThat(notificationJson.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(notificationJson.get("message").asText()).contains("confirmed");

        // The confirmation sender sleeps ~150ms before calling the delivery stub, so the
        // notification must land well after the HTTP response already came back.
        assertThat(notifiedMs - responseMs).isGreaterThanOrEqualTo(100);
        assertThat(responseMs - beforeMs).isLessThan(notifiedMs - beforeMs);
    }

    @Test
    void confirmationFailureDoesNotAffectTheOrderResponse() {
        NOTIFICATION_STATUS.set(500);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        ResponseEntity<Api.OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
    }

    // Lesson 005: refunding a FAILED order.
    @Test
    void refundOfFailedOrderReturns200RefundedWithAmountAndOrderShowsRefunded() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);
        ResponseEntity<Api.OrderResponse> failedResponse =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);
        assertThat(failedResponse.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        Long orderId = failedResponse.getBody().orderId();

        ResponseEntity<Api.RefundResponse> refundResponse =
                rest.postForEntity(url("/api/orders/" + orderId + "/refund"), null, Api.RefundResponse.class);

        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Api.RefundResponse refundBody = refundResponse.getBody();
        assertThat(refundBody).isNotNull();
        assertThat(refundBody.orderId()).isEqualTo(orderId);
        assertThat(refundBody.status()).isEqualTo("REFUNDED");
        assertThat(refundBody.refundedCents()).isEqualTo(1200 + 1400);

        ResponseEntity<Api.OrderResponse> lookup =
                rest.getForEntity(url("/api/orders/" + orderId), Api.OrderResponse.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookup.getBody()).isNotNull();
        assertThat(lookup.getBody().status()).isEqualTo("REFUNDED");
    }

    @Test
    void refundOfConfirmedOrderGives409() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);
        ResponseEntity<Api.OrderResponse> confirmedResponse =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);
        assertThat(confirmedResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long orderId = confirmedResponse.getBody().orderId();

        ResponseEntity<Api.ErrorResponse> refundResponse =
                rest.postForEntity(url("/api/orders/" + orderId + "/refund"), null, Api.ErrorResponse.class);

        assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refundResponse.getBody()).isNotNull();
        assertThat(refundResponse.getBody().message()).isEqualTo("Only failed orders can be refunded");
    }

    @Test
    void secondRefundOfTheSameOrderGives409() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);
        ResponseEntity<Api.OrderResponse> failedResponse =
                rest.postForEntity(url("/api/orders"), request, Api.OrderResponse.class);
        Long orderId = failedResponse.getBody().orderId();

        ResponseEntity<Api.RefundResponse> firstRefund =
                rest.postForEntity(url("/api/orders/" + orderId + "/refund"), null, Api.RefundResponse.class);
        assertThat(firstRefund.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Api.ErrorResponse> secondRefund =
                rest.postForEntity(url("/api/orders/" + orderId + "/refund"), null, Api.ErrorResponse.class);
        assertThat(secondRefund.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondRefund.getBody()).isNotNull();
        assertThat(secondRefund.getBody().message()).isEqualTo("Only failed orders can be refunded");
    }

    @Test
    void refundOfUnknownOrderGives404() {
        ResponseEntity<String> response =
                rest.postForEntity(url("/api/orders/999999/refund"), null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static String awaitNotificationContaining(String needle) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            String candidate = LAST_NOTIFICATION_BODY.get();
            if (candidate != null && candidate.contains(needle)) {
                return candidate;
            }
            Thread.sleep(20);
        }
        return null;
    }

    private static String readBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        // Never let the client reuse this connection: a slow/aborted response in one test
        // must not leave a pooled connection that a later test's fast request could pick up.
        exchange.getResponseHeaders().add("Connection", "close");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
        exchange.close();
    }

    private static void sleepFor(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
