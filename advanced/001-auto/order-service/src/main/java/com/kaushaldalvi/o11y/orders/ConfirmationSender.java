package com.kaushaldalvi.o11y.orders;

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
            try {
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
            }
        };

        // Deliberate: a raw Thread, not an ExecutorService, @Async or a virtual thread.
        // The OpenTelemetry agent auto-propagates trace context across executors but not
        // across a thread started directly like this, so this trace becomes an orphan.
        // Lesson 003 fixes that by hand with Context.current().wrap(...).
        new Thread(task, "confirmation-sender").start();
    }
}
