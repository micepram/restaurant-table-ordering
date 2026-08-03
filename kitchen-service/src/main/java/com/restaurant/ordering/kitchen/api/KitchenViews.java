package com.restaurant.ordering.kitchen.api;

import java.time.Instant;
import java.util.List;

import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.kitchen.domain.KitchenTicket;

public final class KitchenViews {

    private KitchenViews() {
    }

    /** How overdue a ticket is. The board colours on this. */
    public enum Urgency {
        NORMAL,
        WARNING,
        LATE
    }

    /**
     * A ticket as the board renders it.
     *
     * @param waitSeconds age at the moment this view was built. The UI re-derives elapsed
     *                    time locally on a one-second tick rather than asking the server
     *                    again, so the colours advance without any network chatter; this
     *                    value is the anchor it counts from.
     */
    public record TicketView(
            Long orderId,
            Long tableId,
            String tableCode,
            OrderStatus status,
            List<OrderStatus> nextStates,
            Instant placedAt,
            long waitSeconds,
            Urgency urgency,
            long subtotalCents,
            List<TicketLineView> lines) {

        public static TicketView of(KitchenTicket ticket, Instant now, long warnSeconds, long lateSeconds) {
            long waited = ticket.waitSeconds(now);
            return new TicketView(
                    ticket.getOrderId(),
                    ticket.getTableId(),
                    ticket.getTableCode(),
                    ticket.getStatus(),
                    List.copyOf(ticket.getStatus().nextStates()),
                    ticket.getPlacedAt(),
                    waited,
                    urgencyOf(waited, warnSeconds, lateSeconds),
                    ticket.getSubtotalCents(),
                    ticket.getLines().stream().map(TicketLineView::of).toList());
        }

        private static Urgency urgencyOf(long waited, long warnSeconds, long lateSeconds) {
            if (waited >= lateSeconds) {
                return Urgency.LATE;
            }
            if (waited >= warnSeconds) {
                return Urgency.WARNING;
            }
            return Urgency.NORMAL;
        }
    }

    public record TicketLineView(String name, int quantity, String modifiers, String note) {

        public static TicketLineView of(com.restaurant.ordering.kitchen.domain.KitchenTicketLine line) {
            return new TicketLineView(line.getName(), line.getQuantity(), line.getModifiers(), line.getNote());
        }
    }

    /**
     * What goes over the WebSocket.
     *
     * <p>The whole board is sent rather than a delta. It is at most a few dozen tickets,
     * and a client that reconnects or misses a frame is immediately correct again instead
     * of having to replay a delta stream it may have holes in.
     */
    public record BoardUpdate(Instant generatedAt, long warnAfterSeconds, long lateAfterSeconds, List<TicketView> tickets) {
    }
}
