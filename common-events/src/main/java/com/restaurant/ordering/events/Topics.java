package com.restaurant.ordering.events;

/**
 * Kafka topic names.
 *
 * <p>Every topic is keyed by table id so that all events for one table land on the same
 * partition and therefore stay in order relative to each other. Ordering across tables
 * does not matter; ordering within a table does (a status must not overtake the order
 * that created it).
 */
public final class Topics {

    /** Order lifecycle facts, produced only by order-service. */
    public static final String ORDERS = "orders.events";

    /** Kitchen intents (cook advanced a ticket). Not authoritative status. */
    public static final String KITCHEN = "kitchen.events";

    /** Item availability changes and the resulting cache invalidation. */
    public static final String MENU_AVAILABILITY = "menu.availability";

    /** Mock payment results. */
    public static final String PAYMENTS = "payment.events";

    /** Table state and attention flags. */
    public static final String TABLES = "table.events";

    private Topics() {
    }
}
