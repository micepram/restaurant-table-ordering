package com.restaurant.ordering.payment.api;

import com.restaurant.ordering.payment.app.BillNotFoundException;
import com.restaurant.ordering.payment.app.OrderUnavailableException;
import com.restaurant.ordering.payment.app.PaymentDeclinedException;
import com.restaurant.ordering.payment.domain.IllegalBillStateException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns payment failures into problem responses the customer's screen can render. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BillNotFoundException.class)
    public ProblemDetail onNotFound(BillNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Bill not found", ex.getMessage());
    }

    /**
     * 402 Payment Required: the request was valid and the card was refused. Distinguishing
     * this from a 400 matters to the UI — one means "fix your input", the other means
     * "try a different card".
     */
    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail onDeclined(PaymentDeclinedException ex) {
        ProblemDetail detail = problem(HttpStatus.PAYMENT_REQUIRED, "Payment declined", ex.getMessage());
        detail.setProperty("reference", ex.getReference());
        return detail;
    }

    @ExceptionHandler(IllegalBillStateException.class)
    public ProblemDetail onIllegalState(IllegalBillStateException ex) {
        return problem(HttpStatus.CONFLICT, "Bill cannot accept this", ex.getMessage());
    }

    @ExceptionHandler(OrderUnavailableException.class)
    public ProblemDetail onOrderUnavailable(OrderUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Order unavailable", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
