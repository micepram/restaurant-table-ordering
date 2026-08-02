package com.restaurant.ordering.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A cook pressed a button on the kitchen display. Produced by kitchen-service on
 * {@link Topics#KITCHEN}.
 *
 * <p>This is an <em>intent</em>, not a fact: it says what the kitchen wants the order to
 * become. order-service validates it against {@link OrderStatus#canTransitionTo} and, if
 * legal, publishes the corresponding {@link OrderStatusChanged}. An illegal request (a
 * double-tap racing another terminal, say) is dropped rather than applied.
 */
public record KitchenTicketAdvanced(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long tableId,
        OrderStatus requestedStatus,
        String requestedBy) implements DomainEvent {

    @Override
    public String partitionKey() {
        return String.valueOf(tableId);
    }
}
