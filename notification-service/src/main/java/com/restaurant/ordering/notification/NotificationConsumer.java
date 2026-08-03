package com.restaurant.ordering.notification;

import java.util.HashMap;
import java.util.Map;

import com.restaurant.ordering.events.MenuInvalidated;
import com.restaurant.ordering.events.OrderPlaced;
import com.restaurant.ordering.events.OrderStatusChanged;
import com.restaurant.ordering.events.PaymentCompleted;
import com.restaurant.ordering.events.TableEventChanged;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Fans every domain event out to the browsers connected to this instance.
 *
 * <p>Runs under a per-instance consumer group so all instances see all events — see the
 * note on {@code rto.notifications.group}. This service holds no state of its own: it
 * translates events into pushes and nothing else, which is why it has no database.
 *
 * <p>Pushes are notifications, not data transfer. A client that receives one refetches over
 * HTTP rather than trusting the payload, so a missed or duplicated message costs a redundant
 * fetch instead of leaving the UI wrong.
 */
@Component
@KafkaListener(
        topics = {Topics.ORDERS, Topics.MENU_AVAILABILITY, Topics.TABLES, Topics.PAYMENTS},
        groupId = "${rto.notifications.group}")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final SimpMessagingTemplate messaging;

    public NotificationConsumer(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @KafkaHandler
    public void on(OrderPlaced event) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", event.orderId());
        data.put("tableId", event.tableId());
        data.put("status", "PLACED");
        push(Destinations.table(event.tableId()), "ORDER_PLACED", data);
        push(Destinations.order(event.orderId()), "ORDER_PLACED", data);
    }

    /**
     * The status change a customer is actually waiting on — "your food is ready" arrives
     * here without the phone polling for it.
     */
    @KafkaHandler
    public void on(OrderStatusChanged event) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", event.orderId());
        data.put("tableId", event.tableId());
        data.put("previousStatus", event.previousStatus());
        data.put("status", event.status());
        push(Destinations.table(event.tableId()), "ORDER_STATUS", data);
        push(Destinations.order(event.orderId()), "ORDER_STATUS", data);
    }

    /**
     * The last hop of the salmon flow.
     *
     * <p>menu-service publishes this only after the row is written and Redis is evicted, so
     * every client that refetches on receipt is guaranteed to see the new state. Broadcast
     * to all tables because the menu is not table-specific.
     */
    @KafkaHandler
    public void on(MenuInvalidated event) {
        Map<String, Object> data = new HashMap<>();
        data.put("menuItemId", event.menuItemId());
        data.put("itemName", event.itemName());
        data.put("available", event.available());
        push(Destinations.MENU, "MENU_INVALIDATED", data);
    }

    @KafkaHandler
    public void on(PaymentCompleted event) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", event.orderId());
        data.put("tableId", event.tableId());
        data.put("amountCents", event.amountCents());
        data.put("tipCents", event.tipCents());
        data.put("outstandingCents", event.outstandingCents());
        data.put("settledInFull", event.settledInFull());
        push(Destinations.table(event.tableId()), "PAYMENT", data);
        push(Destinations.order(event.orderId()), "PAYMENT", data);
    }

    @KafkaHandler
    public void on(TableEventChanged event) {
        Map<String, Object> data = new HashMap<>();
        data.put("tableId", event.tableId());
        data.put("tableCode", event.tableCode());
        data.put("state", event.state());
        data.put("attentionFlagged", event.attentionFlagged());
        data.put("note", event.note());
        push(Destinations.table(event.tableId()), "TABLE", data);
    }

    /**
     * Catch-all so an event type this service does not translate cannot stall the listener.
     * Without it Spring rejects the unmatched payload and the container redelivers the same
     * record indefinitely.
     */
    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("No notification mapping for {}", event.getClass().getSimpleName());
    }

    private void push(String destination, String type, Map<String, Object> data) {
        messaging.convertAndSend(destination, Notification.of(type, data));
        log.debug("Pushed {} to {}", type, destination);
    }
}
