package com.kaushaldalvi.o11y.orders;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Lesson 002: without the javaagent, @WithSpan is inert, so validate-order,
 * calculate-price and authorize-payment produce no spans here. These tests only assert
 * on what plain OpenTelemetry API calls produce: the tracer-API "fulfil-order" span and
 * the attributes set on Span.current().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracingTests {

    // Lesson 002: registering the extension installs its SDK as GlobalOpenTelemetry for
    // this class (beforeAll) and resets it back afterwards (afterAll), so it does not
    // leak into OrderServiceApplicationTests, which run with no SDK configured at all.
    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private static final HttpServer KITCHEN_SERVER;
    private static final HttpServer DELIVERY_SERVER;

    private static final AtomicLong KITCHEN_DELAY_MILLIS = new AtomicLong(0);
    private static final AtomicInteger KITCHEN_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> KITCHEN_RESPONSE_BODY =
            new AtomicReference<>("{\"ticketId\":\"tk-1\",\"prepMillis\":180}");

    private static final AtomicInteger DELIVERY_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> DELIVERY_RESPONSE_BODY =
            new AtomicReference<>("{\"driver\":\"Priya\",\"etaMinutes\":25}");

    private static final AtomicInteger NOTIFICATION_STATUS = new AtomicInteger(202);

    static {
        try {
            KITCHEN_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            KITCHEN_SERVER.createContext("/tickets", exchange -> {
                readBody(exchange);
                sleepFor(KITCHEN_DELAY_MILLIS.get());
                writeResponse(exchange, KITCHEN_STATUS.get(), KITCHEN_RESPONSE_BODY.get());
            });
            // See OrderServiceApplicationTests: a cached thread pool so a slow (timeout)
            // request in one test cannot delay a later test's fast request.
            KITCHEN_SERVER.setExecutor(Executors.newCachedThreadPool());
            KITCHEN_SERVER.start();

            DELIVERY_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            DELIVERY_SERVER.createContext("/assignments", exchange -> {
                readBody(exchange);
                writeResponse(exchange, DELIVERY_STATUS.get(), DELIVERY_RESPONSE_BODY.get());
            });
            DELIVERY_SERVER.createContext("/notifications", exchange -> {
                readBody(exchange);
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
    }

    @AfterAll
    static void stopServers() {
        KITCHEN_SERVER.stop(0);
        DELIVERY_SERVER.stop(0);
    }

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void resetStubs() {
        KITCHEN_DELAY_MILLIS.set(0);
        KITCHEN_STATUS.set(200);
        KITCHEN_RESPONSE_BODY.set("{\"ticketId\":\"tk-1\",\"prepMillis\":180}");

        DELIVERY_STATUS.set(200);
        DELIVERY_RESPONSE_BODY.set("{\"driver\":\"Priya\",\"etaMinutes\":25}");

        NOTIFICATION_STATUS.set(202);
    }

    @Test
    void confirmedOrderProducesAFinishedFulfilOrderSpanWithUnsetStatus() {
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        Api.OrderResponse response = orderService.placeOrder(request);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        SpanData fulfilOrderSpan = findSpan("fulfil-order");
        assertThat(fulfilOrderSpan.hasEnded()).isTrue();
        assertThat(fulfilOrderSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void slowKitchenLikeSlowNoodlesProducesAFulfilOrderSpanWithErrorStatus() {
        // Same shape as the built-in Slow Noodles timeout: the stub takes longer than the
        // configured read timeout, so the kitchen call fails.
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        try {
            orderService.placeOrder(request);
        } catch (Exceptions.DownstreamFailureException expected) {
            // Calling the service bean directly, rather than over HTTP, skips the
            // @ExceptionHandler that turns this into a 504 response.
        }

        SpanData fulfilOrderSpan = findSpan("fulfil-order");
        assertThat(fulfilOrderSpan.hasEnded()).isTrue();
        assertThat(fulfilOrderSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(fulfilOrderSpan.getStatus().getDescription()).isEqualTo("downstream call failed");
    }

    @Test
    void confirmedOrderInsideAParentSpanCarriesOrderAttributes() {
        Api.OrderResponse response = placeOrderUnderTestParentSpan(
                new Api.OrderRequest(1L, List.of(1L, 2L), null), "confirmed-parent");

        assertThat(response.status()).isEqualTo("CONFIRMED");
        SpanData parentSpan = findSpan("confirmed-parent");
        assertThat(parentSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(parentSpan.getAttributes().get(AttributeKey.longKey("order.id"))).isEqualTo(response.orderId());
        assertThat(parentSpan.getAttributes().get(AttributeKey.longKey("restaurant.id"))).isEqualTo(1L);
        assertThat(parentSpan.getAttributes().get(AttributeKey.longKey("order.total_cents")))
                .isEqualTo((long) (1200 + 1400));
        assertThat(parentSpan.getAttributes().get(AttributeKey.longKey("order.item_count"))).isEqualTo(2L);
        assertThat(parentSpan.getAttributes().get(AttributeKey.booleanKey("payment.declined"))).isEqualTo(false);
    }

    @Test
    void declinedOrderInsideAParentSpanCarriesPaymentDeclinedTrueAndLeavesStatusUnset() {
        Api.OrderResponse response = placeOrderUnderTestParentSpan(
                new Api.OrderRequest(1L, List.of(1L, 2L), "4242424242420000"), "declined-parent");

        assertThat(response.status()).isEqualTo("DECLINED");
        SpanData parentSpan = findSpan("declined-parent");
        assertThat(parentSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(parentSpan.getAttributes().get(AttributeKey.booleanKey("payment.declined"))).isEqualTo(true);
    }

    /**
     * Starts a span with the extension's tracer, makes it current on this thread, and
     * calls the service bean directly (not over HTTP) so the thread-local context
     * applies, then ends the parent span so it shows up as a finished span.
     */
    private Api.OrderResponse placeOrderUnderTestParentSpan(Api.OrderRequest request, String parentSpanName) {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");
        Span parent = tracer.spanBuilder(parentSpanName).startSpan();
        try (Scope scope = parent.makeCurrent()) {
            return orderService.placeOrder(request);
        } finally {
            parent.end();
        }
    }

    private SpanData findSpan(String name) {
        List<SpanData> matches = otelTesting.getSpans().stream()
                .filter(span -> span.getName().equals(name))
                .toList();
        assertThat(matches).as("exactly one span named '%s'", name).hasSize(1);
        return matches.get(0);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exchange.getRequestBody().transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
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
