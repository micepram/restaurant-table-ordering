package com.restaurant.ordering.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A customer submitted an order. Produced by order-service on {@link Topics#ORDERS}.
 *
 * <p>This is the fan-in event: kitchen-service consumes it from every table and merges
 * the results into one queue ordered by {@link #placedAt()}.
 */
public record OrderPlaced(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long tableId,
        String tableCode,
        Instant placedAt,
        List<OrderLine> lines,
        long subtotalCents) implements DomainEvent {

    public OrderPlaced {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    @Override
    public String partitionKey() {
        return String.valueOf(tableId);
    }
}
