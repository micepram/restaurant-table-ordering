package com.restaurant.ordering.order.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** menu-service could not be reached, so the order cannot be priced or validated. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MenuUnavailableException extends RuntimeException {

    public MenuUnavailableException(String message) {
        super(message);
    }

    public MenuUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
