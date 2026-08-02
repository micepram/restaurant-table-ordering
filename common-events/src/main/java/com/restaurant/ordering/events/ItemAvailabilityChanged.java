package com.restaurant.ordering.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The kitchen 86'd an item (or put it back on). Produced by kitchen-service on
 * {@link Topics#MENU_AVAILABILITY}; consumed by menu-service, which is the owner of
 * menu state and applies it to the database and the Redis cache.
 *
 * <p>All menu-availability events share one partition key so they are applied in the
 * order they were issued — an off-then-on toggle must not land reversed.
 */
public record ItemAvailabilityChanged(
        UUID eventId,
        Instant occurredAt,
        Long menuItemId,
        boolean available,
        String reason,
        String changedBy) implements DomainEvent {

    static final String MENU_PARTITION_KEY = "menu";

    @Override
    public String partitionKey() {
        return MENU_PARTITION_KEY;
    }
}
