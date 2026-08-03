package com.restaurant.ordering.table.app;

import com.restaurant.ordering.events.OrderPlaced;
import com.restaurant.ordering.events.PaymentCompleted;
import com.restaurant.ordering.events.TableState;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Advances table state as orders and payments happen, so the staff dashboard reflects the
 * room without anyone having to update it by hand.
 *
 * <pre>
 *   first order placed  -> ORDERING
 *   bill settled        -> SETTLED   (a waiter clears the table back to FREE)
 * </pre>
 *
 * <p>Only forward moves are applied. A late or redelivered {@code OrderPlaced} for a table
 * that has already settled must not drag it back to ORDERING — Kafka is at-least-once, and
 * a table state that flickers backwards is worse than one that lags.
 *
 * <p>Runs under table-service's own stable group: this is a write to owned state, so
 * exactly one instance should apply each event.
 */
@Component
@KafkaListener(topics = {Topics.ORDERS, Topics.PAYMENTS}, groupId = "table-service")
public class TableStateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TableStateConsumer.class);

    private final TableAppService tableService;

    public TableStateConsumer(TableAppService tableService) {
        this.tableService = tableService;
    }

    @KafkaHandler
    public void on(OrderPlaced event) {
        tableService.advanceStateOnOrder(event.tableId());
    }

    @KafkaHandler
    public void on(PaymentCompleted event) {
        if (!event.settledInFull()) {
            return;
        }
        tableService.advanceStateOnSettlement(event.tableId());
    }

    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("No table-state mapping for {}", event.getClass().getSimpleName());
    }
}
