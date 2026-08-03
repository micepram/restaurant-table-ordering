package com.restaurant.ordering.notification;

/**
 * STOMP destinations the customer app subscribes to.
 *
 * <p>Per-table and per-order destinations are what make authorisation possible at all: the
 * table id is embedded in the destination string, so the subscription interceptor can
 * compare it against the token's {@code tableId} claim and refuse a diner at table 3 who
 * asks for table 4's stream. A single shared destination would leave every customer
 * receiving every table's traffic.
 */
public final class Destinations {

    /** Menu-wide invalidation. Broadcast to everyone — it carries no table-specific data. */
    public static final String MENU = "/topic/menu";

    private Destinations() {
    }

    /** Everything happening at one table: order status, payment, attention flags. */
    public static String table(Long tableId) {
        return "/topic/tables/" + tableId;
    }

    /** One order's own status stream, for a customer tracking a specific order. */
    public static String order(Long orderId) {
        return "/topic/orders/" + orderId;
    }
}
