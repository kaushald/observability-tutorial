package com.kaushaldalvi.o11y.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaushaldalvi.o11y.orders.dto.MenuItemResponse;
import com.kaushaldalvi.o11y.orders.dto.OrderRequest;
import com.kaushaldalvi.o11y.orders.dto.OrderResponse;
import com.kaushaldalvi.o11y.orders.dto.RestaurantResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    }

    @AfterAll
    static void stopServers() {
        KITCHEN_SERVER.stop(0);
        DELIVERY_SERVER.stop(0);
    }

    @LocalServerPort
    private int port;

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
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void restaurantsListReturnsTheThreeSeededRestaurants() {
        ResponseEntity<RestaurantResponse[]> response =
                rest.getForEntity(url("/api/restaurants"), RestaurantResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<RestaurantResponse> restaurants = List.of(response.getBody());
        assertThat(restaurants).hasSize(3);
        assertThat(restaurants).extracting(RestaurantResponse::name)
                .containsExactlyInAnyOrder("Bella Pizza", "Slow Noodles", "Taco Corner");
    }

    @Test
    void menuForRestaurant1Has4ItemsAndUnknownRestaurantGives404() {
        ResponseEntity<MenuItemResponse[]> okResponse =
                rest.getForEntity(url("/api/restaurants/1/menu"), MenuItemResponse[].class);
        assertThat(okResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(okResponse.getBody()).hasSize(4);

        ResponseEntity<String> notFound = rest.getForEntity(url("/api/restaurants/999/menu"), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void happyPathOrderReturns201ConfirmedWithDriverAndCorrectTotal() throws Exception {
        OrderRequest request = new OrderRequest(1L, List.of(1L, 2L));

        ResponseEntity<OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
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
        OrderRequest request = new OrderRequest(1L, List.of(1L, 2L));

        ResponseEntity<OrderResponse> response =
                rest.postForEntity(url("/api/orders"), request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("FAILED");
        assertThat(body.message()).isNotNull();

        ResponseEntity<OrderResponse> lookup =
                rest.getForEntity(url("/api/orders/" + body.orderId()), OrderResponse.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookup.getBody()).isNotNull();
        assertThat(lookup.getBody().status()).isEqualTo("FAILED");
    }

    @Test
    void itemsFromAnotherRestaurantGive400() {
        OrderRequest request = new OrderRequest(1L, List.of(1L, 5L));

        ResponseEntity<String> response = rest.postForEntity(url("/api/orders"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("message");
    }

    @Test
    void unknownRestaurantOnOrderGives400() {
        OrderRequest request = new OrderRequest(999L, List.of(1L));

        ResponseEntity<String> response = rest.postForEntity(url("/api/orders"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
