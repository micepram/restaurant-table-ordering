package com.restaurant.ordering.order.domain;

import com.restaurant.ordering.events.OrderStatus;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when something asks for a status move the state machine does not permit. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalOrderTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalOrderTransitionException(Long orderId, OrderStatus from, OrderStatus to, String why) {
        super("Order %s cannot move %s -> %s: %s".formatted(orderId, from, to, why));
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
