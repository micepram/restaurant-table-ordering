package com.restaurant.ordering.table.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TableNotFoundException extends RuntimeException {

    public TableNotFoundException(String message) {
        super(message);
    }
}
