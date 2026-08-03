package com.restaurant.ordering.payment.domain;

/** The bill cannot accept this operation in its current state. */
public class IllegalBillStateException extends RuntimeException {

    public IllegalBillStateException(String message) {
        super(message);
    }
}
