package com.restaurant.ordering.order.api;

import com.restaurant.ordering.order.app.InvalidOrderException;
import com.restaurant.ordering.order.app.MenuUnavailableException;
import com.restaurant.ordering.order.app.OrderNotFoundException;
import com.restaurant.ordering.order.domain.IllegalOrderTransitionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain failures into RFC 9457 problem responses that carry the reason.
 *
 * <p>Written explicitly rather than relying on {@code server.error.include-message}: the
 * default error body drops the message, so a rejected order arrives at the customer's phone
 * as a bare 422 with nothing to display. These messages are user-facing — "Ribeye Steak
 * requires at least 1 choice from Cooked to" is the whole point of the validation.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail onInvalidOrder(InvalidOrderException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Order rejected", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail onNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
    }

    /**
     * 409 rather than 422: the request was well formed, it just raced another actor or
     * arrived out of order. The kitchen display uses this to re-sync instead of retrying.
     */
    @ExceptionHandler(IllegalOrderTransitionException.class)
    public ProblemDetail onIllegalTransition(IllegalOrderTransitionException ex) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Status change not allowed", ex.getMessage());
        detail.setProperty("from", ex.getFrom());
        detail.setProperty("to", ex.getTo());
        return detail;
    }

    @ExceptionHandler(MenuUnavailableException.class)
    public ProblemDetail onMenuUnavailable(MenuUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Menu unavailable", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
