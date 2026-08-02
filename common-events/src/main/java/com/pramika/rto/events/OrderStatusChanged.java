package com.pramika.rto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The authoritative record that an order moved state. Produced <em>only</em> by
 * order-service on {@link Topics#ORDERS}, after it has validated the transition.
 *
 * <p>Consumers (kitchen board, customer tracker, staff dashboard) treat this as truth.
 * Nothing else may publish it — that single-writer rule is what keeps the kitchen and
 * the order aggregate from diverging.
 */
public record OrderStatusChanged(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long tableId,
        OrderStatus previousStatus,
        OrderStatus status) implements DomainEvent {

    @Override
    public String partitionKey() {
        return String.valueOf(tableId);
    }
}
