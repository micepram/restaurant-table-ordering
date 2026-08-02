package com.pramika.rto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A table changed state or was flagged for attention. Produced by table-service on
 * {@link Topics#TABLES} and fanned out to the staff dashboard.
 *
 * @param attentionFlagged true when a customer or staff member has asked for someone to
 *                         come over; independent of {@link #state()} so a table can want
 *                         attention in any state
 */
public record TableEventChanged(
        UUID eventId,
        Instant occurredAt,
        Long tableId,
        String tableCode,
        TableState state,
        boolean attentionFlagged,
        String note) implements DomainEvent {

    @Override
    public String partitionKey() {
        return String.valueOf(tableId);
    }
}
