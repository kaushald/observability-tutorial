package com.kaushaldalvi.o11y.orders;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    /**
     * Simulates authorizing a card. Declines a card whose digits (ignoring spaces and
     * dashes) end in "0000"; approves everything else, including a missing card number.
     */
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
}
