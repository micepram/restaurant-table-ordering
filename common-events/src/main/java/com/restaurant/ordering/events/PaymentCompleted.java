package com.restaurant.ordering.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A (mock) payment settled. Produced by payment-service on {@link Topics#PAYMENTS}.
 *
 * <p>order-service consumes it and, if the order is READY, moves it to PAID — the same
 * single-writer rule that applies to kitchen intents. A bill split across several payers
 * emits one of these per share; order-service only advances once the outstanding balance
 * reaches zero.
 */
public record PaymentCompleted(
        UUID eventId,
        Instant occurredAt,
        Long paymentId,
        Long orderId,
        Long tableId,
        long amountCents,
        long tipCents,
        long outstandingCents,
        boolean settledInFull) implements DomainEvent {

    @Override
    public String partitionKey() {
        return String.valueOf(tableId);
    }
}
