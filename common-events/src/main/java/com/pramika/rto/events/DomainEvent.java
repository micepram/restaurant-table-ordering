package com.pramika.rto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for everything published to Kafka.
 *
 * <p>{@link #partitionKey()} is what the producer uses as the record key. Events that
 * belong to a table return that table's id so their relative order is preserved;
 * menu-wide events return a constant so they land together and are applied in order.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String partitionKey();
}
