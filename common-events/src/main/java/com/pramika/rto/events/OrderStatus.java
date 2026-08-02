package com.pramika.rto.events;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The transition table lives here, next to the enum, because both
 * order-service (which enforces it) and the UIs (which render progress) depend on it.
 *
 * <p>{@code order-service} is the only component permitted to apply a transition.
 * The kitchen expresses an <em>intent</em> via {@link KitchenTicketAdvanced}; order-service
 * validates it against this table and publishes the resulting fact.
 */
public enum OrderStatus {

    PLACED,
    ACKNOWLEDGED,
    PREPARING,
    READY,
    PAID,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PLACED, EnumSet.of(ACKNOWLEDGED, CANCELLED),
            ACKNOWLEDGED, EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(READY, CANCELLED),
            // Payment is independent of cooking: a table can settle up while food is
            // still on the pass, so READY -> PAID and also PREPARING -> PAID would be
            // plausible in a real venue. We keep payment strictly after READY to make
            // the demo's state machine legible.
            READY, EnumSet.of(PAID),
            PAID, Collections.emptySet(),
            CANCELLED, Collections.emptySet());

    /** @return true if this status may legally move to {@code next}. */
    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** @return the statuses reachable in one step from this one. */
    public Set<OrderStatus> nextStates() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
