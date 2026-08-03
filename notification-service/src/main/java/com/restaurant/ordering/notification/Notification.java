package com.restaurant.ordering.notification;

import java.time.Instant;
import java.util.Map;

/**
 * The single envelope every push uses.
 *
 * <p>One shape for all notification types keeps the client's handling uniform: it switches
 * on {@link #type()} rather than guessing from the destination it arrived on. {@code data}
 * is an open map because these are display payloads, not contracts the client computes with
 * — anything it needs to act on, it refetches over HTTP.
 */
public record Notification(String type, Instant at, Map<String, Object> data) {

    public static Notification of(String type, Map<String, Object> data) {
        return new Notification(type, Instant.now(), data);
    }
}
