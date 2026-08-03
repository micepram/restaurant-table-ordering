package com.restaurant.ordering.events;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order lifecycle.
 *
 * <p>This table is the system's central invariant: the kitchen, payment-service and the
 * staff dashboard all express intents, and order-service admits a change only if this
 * permits it. A hole here is a hole in every one of those paths at once, so the illegal
 * transitions are enumerated as carefully as the legal ones.
 */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PLACED, ACKNOWLEDGED",
            "PLACED, CANCELLED",
            "ACKNOWLEDGED, PREPARING",
            "ACKNOWLEDGED, CANCELLED",
            "PREPARING, READY",
            "PREPARING, CANCELLED",
            "READY, PAID",
    })
    void allowsLegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            // Skipping ahead: the kitchen cannot mark food ready it never started.
            "PLACED, PREPARING",
            "PLACED, READY",
            "PLACED, PAID",
            "ACKNOWLEDGED, READY",
            "ACKNOWLEDGED, PAID",
            // Paying for food that is not ready.
            "PREPARING, PAID",
            // Going backwards.
            "ACKNOWLEDGED, PLACED",
            "PREPARING, ACKNOWLEDGED",
            "READY, PREPARING",
            // Cancelling once the customer has paid.
            "READY, CANCELLED",
            "PAID, CANCELLED",
            "PAID, READY",
            // Reviving a cancelled order.
            "CANCELLED, PLACED",
            "CANCELLED, PREPARING",
    })
    void refusesIllegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest(name = "{0} cannot transition to itself")
    @EnumSource(OrderStatus.class)
    void refusesSelfTransitions(OrderStatus status) {
        // A duplicate Kafka intent arrives as a same-state request. It must be refused so
        // order-service drops it rather than emitting a second status-change event.
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    @DisplayName("PAID and CANCELLED are terminal")
    void terminalStatesHaveNoSuccessors() {
        assertThat(OrderStatus.PAID.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.PAID.nextStates()).isEmpty();
        assertThat(OrderStatus.CANCELLED.nextStates()).isEmpty();
    }

    @ParameterizedTest(name = "{0} is not terminal")
    @EnumSource(value = OrderStatus.class, names = {"PLACED", "ACKNOWLEDGED", "PREPARING", "READY"})
    void inFlightStatesAreNotTerminal(OrderStatus status) {
        assertThat(status.isTerminal()).isFalse();
        assertThat(status.nextStates()).isNotEmpty();
    }

    @Test
    @DisplayName("every status is reachable from PLACED, so no state is stranded")
    void everyStatusIsReachable() {
        Set<OrderStatus> reached = EnumSet.of(OrderStatus.PLACED);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (OrderStatus status : EnumSet.copyOf(reached)) {
                for (OrderStatus next : status.nextStates()) {
                    grew |= reached.add(next);
                }
            }
        }
        assertThat(reached).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    @DisplayName("the happy path runs placed to paid without a gap")
    void happyPathIsContiguous() {
        OrderStatus[] path = {
                OrderStatus.PLACED, OrderStatus.ACKNOWLEDGED,
                OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.PAID,
        };
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(path[i].canTransitionTo(path[i + 1]))
                    .as("%s -> %s", path[i], path[i + 1])
                    .isTrue();
        }
    }

    @Test
    @DisplayName("nextStates cannot be mutated by a caller")
    void nextStatesIsUnmodifiable() {
        // The UI receives this set and renders a button per entry; if a caller could mutate
        // it, one screen could corrupt the transition table for the whole JVM.
        Set<OrderStatus> next = OrderStatus.PLACED.nextStates();
        assertThat(next).containsExactlyInAnyOrder(OrderStatus.ACKNOWLEDGED, OrderStatus.CANCELLED);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> next.add(OrderStatus.PAID))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
