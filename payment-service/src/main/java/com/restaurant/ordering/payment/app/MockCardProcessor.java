package com.restaurant.ordering.payment.app;

import java.time.Duration;
import java.util.UUID;

import com.restaurant.ordering.payment.domain.Payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a card acquirer.
 *
 * <p>Deterministic rather than random, keyed off the card number's last four digits, so a
 * demo or a test can force a specific outcome instead of retrying until it happens:
 *
 * <ul>
 *   <li>{@code ...0000} — declined</li>
 *   <li>{@code ...0001} — processor failure (the "we don't know if it went through" case)</li>
 *   <li>anything else — approved</li>
 * </ul>
 *
 * <p>The card number is never stored or logged; only the last four digits survive this class.
 */
@Component
public class MockCardProcessor {

    private static final Logger log = LoggerFactory.getLogger(MockCardProcessor.class);

    private final Duration simulatedLatency;

    public MockCardProcessor(@Value("${rto.payment.simulated-latency:400ms}") Duration simulatedLatency) {
        this.simulatedLatency = simulatedLatency;
    }

    public Result authorise(String cardNumber, long amountCents) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\D", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;

        // Real acquirers take a moment; the UI needs to cope with that rather than
        // assuming payment is instant.
        try {
            Thread.sleep(simulatedLatency.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(Payment.Status.FAILED, last4, "Interrupted", reference());
        }

        log.debug("Authorising {} cents on card ending {}", amountCents, last4);

        return switch (last4) {
            case "0000" -> new Result(Payment.Status.DECLINED, last4, "Card declined", reference());
            case "0001" -> new Result(Payment.Status.FAILED, last4, "Processor unavailable", reference());
            default -> new Result(Payment.Status.APPROVED, last4, null, reference());
        };
    }

    private String reference() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public record Result(Payment.Status status, String cardLast4, String failureReason, String reference) {

        public boolean approved() {
            return status == Payment.Status.APPROVED;
        }
    }
}
