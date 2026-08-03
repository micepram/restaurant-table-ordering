package com.restaurant.ordering.order.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The order cannot be accepted as submitted — an unavailable item, an unknown modifier, or
 * a modifier group whose min/max rules are not satisfied.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
