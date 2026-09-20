package com.kaushaldalvi.o11y.orders;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ConfirmationSender {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationSender.class);

    private final RestClient deliveryRestClient;

    public ConfirmationSender(@Qualifier("deliveryRestClient") RestClient deliveryRestClient) {
        this.deliveryRestClient = deliveryRestClient;
    }

    public void sendAsync(long orderId, String driver, int etaMinutes) {
        Runnable task = () -> {
            // Lesson 003: a span for the background work. It becomes a child of whatever
            // span is current on THIS thread, which is nothing unless context was carried over.
            Span span = GlobalOpenTelemetry.getTracer("order-service")
                    .spanBuilder("send-confirmation")
                    .setAttribute("order.id", orderId)
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                Thread.sleep(150);
                String message = "Your order is confirmed. " + driver + " arrives in " + etaMinutes + " minutes.";
                deliveryRestClient.post()
                        .uri("/notifications")
                        .body(new Api.ConfirmationRequest(orderId, message))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Confirmation for order {} sent", orderId);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Confirmation for order {} failed", orderId);
            } finally {
                span.end();
            }
        };

        // Still a raw Thread: the agent carries trace context across executors, but not
        // across a thread started directly like this.
        // Lesson 003: so carry it by hand. Context.current() is captured here, on the
        // request thread, and wrap() makes it current again inside the new thread. Remove
        // the wrap() to see the confirmation fall out of the order's trace.
        new Thread(Context.current().wrap(task), "confirmation-sender").start();
    }
}
