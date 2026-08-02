package com.restaurant.ordering.events;

import java.time.Instant;
import java.util.UUID;

/**
 * menu-service has updated the menu and evicted its Redis cache. Produced by menu-service
 * on {@link Topics#MENU_AVAILABILITY} <em>after</em> the write and the eviction have both
 * committed, so any client that refetches on receipt is guaranteed to read the new state.
 *
 * <p>notification-service fans this out to every open table session, which is what makes
 * "we just ran out of salmon" appear on the customer's phone without a refresh.
 */
public record MenuInvalidated(
        UUID eventId,
        Instant occurredAt,
        Long menuItemId,
        boolean available,
        String itemName) implements DomainEvent {

    @Override
    public String partitionKey() {
        return ItemAvailabilityChanged.MENU_PARTITION_KEY;
    }
}
