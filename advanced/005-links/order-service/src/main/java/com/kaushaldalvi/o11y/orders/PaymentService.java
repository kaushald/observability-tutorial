package com.kaushaldalvi.o11y.orders;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    /**
     * Simulates authorizing a card. Declines a card whose digits (ignoring spaces and
     * dashes) end in "0000"; approves everything else, including a missing card number.
     */
    // Lesson 002: manual span with the annotation. This is the 80ms gap lesson 001
    // pointed out: no network or database call, just a delay, now visible as its own span.
    @WithSpan("authorize-payment")
    public boolean authorize(String cardNumber, int totalCents) {
        simulateProcessingDelay();

        if (cardNumber == null || cardNumber.isBlank()) {
            return true;
        }

        String digits = cardNumber.replaceAll("[ -]", "");
        return !digits.endsWith("0000");
    }

    private void simulateProcessingDelay() {
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates issuing a refund for a previously authorized payment.
     */
    // Lesson 005: manual span with the annotation, same as authorize-payment above.
    @WithSpan("refund-payment")
    public void refund(long orderId, int amountCents) {
        simulateRefundDelay();
    }

    private void simulateRefundDelay() {
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
