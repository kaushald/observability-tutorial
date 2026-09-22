package com.kaushaldalvi.o11y.orders;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
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
import java.util.Optional;
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

    @Autowired
    private CustomerOrderRepository orderRepository;

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

    // Lesson 003: the confirmation runs on its own thread. Its span must still belong to
    // the trace of the order that caused it.
    @Test
    void confirmationSpanIsAChildOfTheSpanThatPlacedTheOrder() throws InterruptedException {
        Api.OrderResponse response = placeOrderUnderTestParentSpan(
                new Api.OrderRequest(1L, List.of(1L), null), "async-parent");
        assertThat(response.status()).isEqualTo("CONFIRMED");

        SpanData confirmation = awaitConfirmationSpan(response.orderId());
        SpanData parentSpan = findSpan("async-parent");
        assertThat(confirmation.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(confirmation.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
    }

    // Matches on order.id as well as the name: a confirmation thread started by an
    // earlier test can still finish while this one runs.
    private static SpanData awaitConfirmationSpan(long orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            for (SpanData span : otelTesting.getSpans()) {
                Long spanOrderId = span.getAttributes().get(AttributeKey.longKey("order.id"));
                if (span.getName().equals("send-confirmation") && spanOrderId != null && spanOrderId == orderId) {
                    return span;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("no finished send-confirmation span for order " + orderId + " within 3 seconds");
    }

    // Lesson 004: span events mark moments inside a span, rather than describing work
    // with a duration. order.validated and payment.authorized happen while placeOrder's
    // caller span (the parent here, the server span in production) is current;
    // kitchen.accepted and driver.assigned happen inside fulfilOrder, so they land on
    // fulfil-order instead.
    @Test
    void confirmedOrderRecordsEventsOnParentAndFulfilOrderSpans() {
        Api.OrderResponse response = placeOrderUnderTestParentSpan(
                new Api.OrderRequest(1L, List.of(1L, 2L), null), "events-confirmed-parent");

        assertThat(response.status()).isEqualTo("CONFIRMED");
        SpanData parentSpan = findSpan("events-confirmed-parent");
        List<String> parentEventNames = eventNames(parentSpan);
        assertThat(parentEventNames).containsSubsequence("order.validated", "payment.authorized");
        assertThat(parentEventNames).doesNotContain("payment.declined");

        EventData paymentAuthorized = findEvent(parentSpan, "payment.authorized");
        assertThat(paymentAuthorized.getAttributes().get(AttributeKey.longKey("payment.amount_cents")))
                .isEqualTo((long) response.totalCents());

        SpanData fulfilOrderSpan = findSpan("fulfil-order");
        List<String> fulfilEventNames = eventNames(fulfilOrderSpan);
        assertThat(fulfilEventNames).containsSubsequence("kitchen.accepted", "driver.assigned");

        EventData driverAssigned = findEvent(fulfilOrderSpan, "driver.assigned");
        assertThat(driverAssigned.getAttributes().get(AttributeKey.stringKey("delivery.driver"))).isEqualTo("Priya");
    }

    @Test
    void declinedOrderRecordsValidatedThenDeclinedEventsAndNoFulfilOrderSpan() {
        Api.OrderResponse response = placeOrderUnderTestParentSpan(
                new Api.OrderRequest(1L, List.of(1L, 2L), "4242424242420000"), "events-declined-parent");

        assertThat(response.status()).isEqualTo("DECLINED");
        SpanData parentSpan = findSpan("events-declined-parent");
        List<String> parentEventNames = eventNames(parentSpan);
        assertThat(parentEventNames).containsSubsequence("order.validated", "payment.declined");

        EventData declined = findEvent(parentSpan, "payment.declined");
        assertThat(declined.getAttributes().get(AttributeKey.stringKey("payment.reason"))).isEqualTo("card_declined");

        // No fulfil-order span for this order: the payment step returns before fulfilOrder
        // is ever called. Other tests' background confirmation threads can still finish
        // during this test, so filter rather than assert on the total span count.
        assertThat(otelTesting.getSpans()).noneMatch(span -> span.getName().equals("fulfil-order"));
    }

    @Test
    void slowKitchenFailureRecordsExceptionEventOnFulfilOrderSpan() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        try {
            orderService.placeOrder(request);
        } catch (Exceptions.DownstreamFailureException expected) {
            // See slowKitchenLikeSlowNoodlesProducesAFulfilOrderSpanWithErrorStatus above.
        }

        SpanData fulfilOrderSpan = findSpan("fulfil-order");
        assertThat(fulfilOrderSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        EventData exceptionEvent = findEvent(fulfilOrderSpan, "exception");
        String exceptionType = exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.type"));
        assertThat(exceptionType).isNotBlank();
        assertThat(exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"))).isNotBlank();
    }

    // Lesson 005: a refund happens outside the order's trace entirely. Its span must
    // link back to the span that placed the order, rather than being that span's child.
    @Test
    void refundOrderLinksBackToTheOriginalOrdersSpanFromADifferentTraceWithNoParent() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        Long orderId;
        try {
            placeOrderUnderTestParentSpan(request, "refund-source-parent");
            throw new AssertionError("expected DownstreamFailureException");
        } catch (Exceptions.DownstreamFailureException e) {
            orderId = e.getOrderId();
        }
        SpanData originalParentSpan = findSpan("refund-source-parent");

        // Called with no current span: the refund's trace must not attach to the order's.
        Optional<Api.RefundResponse> refundResponse = orderService.refundOrder(orderId);

        assertThat(refundResponse).isPresent();
        assertThat(refundResponse.get().status()).isEqualTo("REFUNDED");

        SpanData refundSpan = findSpan("refund-order");
        assertThat(refundSpan.getAttributes().get(AttributeKey.longKey("order.id"))).isEqualTo(orderId);

        List<LinkData> links = refundSpan.getLinks();
        assertThat(links).hasSize(1);
        LinkData link = links.get(0);
        assertThat(link.getSpanContext().getTraceId()).isEqualTo(originalParentSpan.getTraceId());
        assertThat(link.getSpanContext().getSpanId()).isEqualTo(originalParentSpan.getSpanId());
        assertThat(link.getAttributes().get(AttributeKey.stringKey("link.reason"))).isEqualTo("original-order");

        assertThat(refundSpan.getTraceId()).isNotEqualTo(originalParentSpan.getTraceId());
        assertThat(refundSpan.getParentSpanContext().isValid()).isFalse();

        EventData refunded = findEvent(refundSpan, "payment.refunded");
        assertThat(refunded.getAttributes().get(AttributeKey.longKey("payment.amount_cents"))).isNotNull();
    }

    // Lesson 005: an order placed with nothing current when placeOrder runs has no span
    // context to store, so its refund has nothing to link to - and must not throw trying.
    @Test
    void orderPlacedWithNoCurrentSpanStoresNullTraceAndSpanIdsAndItsRefundHasNoLinks() {
        KITCHEN_DELAY_MILLIS.set(800);
        Api.OrderRequest request = new Api.OrderRequest(1L, List.of(1L, 2L), null);

        Long orderId;
        try {
            orderService.placeOrder(request);
            throw new AssertionError("expected DownstreamFailureException");
        } catch (Exceptions.DownstreamFailureException e) {
            orderId = e.getOrderId();
        }

        CustomerOrder savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getTraceId()).isNull();
        assertThat(savedOrder.getSpanId()).isNull();

        Optional<Api.RefundResponse> refundResponse = orderService.refundOrder(orderId);
        assertThat(refundResponse).isPresent();
        assertThat(refundResponse.get().status()).isEqualTo("REFUNDED");

        SpanData refundSpan = findSpan("refund-order");
        assertThat(refundSpan.getLinks()).isEmpty();
    }

    private static List<String> eventNames(SpanData span) {
        return span.getEvents().stream().map(EventData::getName).toList();
    }

    private static EventData findEvent(SpanData span, String name) {
        List<EventData> matches = span.getEvents().stream()
                .filter(event -> event.getName().equals(name))
                .toList();
        assertThat(matches).as("exactly one event named '%s' on span '%s'", name, span.getName()).hasSize(1);
        return matches.get(0);
    }
}
