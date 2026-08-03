package com.restaurant.ordering.payment.app;

/** order-service could not be reached, so the bill cannot be opened. */
public class OrderUnavailableException extends RuntimeException {

    public OrderUnavailableException(String message) {
        super(message);
    }

    public OrderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
