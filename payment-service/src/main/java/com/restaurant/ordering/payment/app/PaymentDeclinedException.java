package com.restaurant.ordering.payment.app;

/** The processor refused the card. The attempt is still recorded. */
public class PaymentDeclinedException extends RuntimeException {

    private final String reference;

    public PaymentDeclinedException(String message, String reference) {
        super(message);
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
