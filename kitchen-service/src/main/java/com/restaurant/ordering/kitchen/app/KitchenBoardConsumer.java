package com.restaurant.ordering.kitchen.app;

import com.restaurant.ordering.events.OrderPlaced;
import com.restaurant.ordering.events.OrderStatusChanged;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The fan-in. Orders from every table arrive here and become one kitchen queue.
 *
 * <p>Ordering across tables does not need to be preserved on the wire: the board's sort key
 * is when each order was <em>placed</em>, not when its event happened to be consumed, so
 * three partitions delivering concurrently still produce one correct oldest-first queue.
 *
 * <h2>Why one consumer instead of two</h2>
 *
 * <p>The projection and the WebSocket push want opposite delivery semantics — the ticket
 * table should be written once, while every instance needs every event so it can push to
 * the browsers it holds. Splitting them into a stable group and a per-instance group looks
 * right and is subtly broken: the two consumers read the topic independently, so the
 * broadcasting one can build the board <em>before</em> the projecting one has committed,
 * and nothing ever re-sends. The board would silently miss a ticket until the next
 * unrelated event.
 *
 * <p>So this is one listener under a <strong>per-instance</strong> group
 * ({@code kitchen-ws-${random.uuid}}): it applies the projection and then broadcasts, in
 * that order, in the same handler. Every instance sees every event, so every display gets
 * updated. A shared group id would instead deliver each event to one instance only, leaving
 * boards on the other instances quietly stale — a failure invisible in single-instance dev
 * and worst under scale-out.
 *
 * <p>The cost is that each instance writes the same projection rows. Those writes are
 * idempotent (keyed by order id, guarded by an existence check), so they converge; it is
 * write amplification, not a correctness problem.
 */
@Component
@KafkaListener(topics = Topics.ORDERS, groupId = "${rto.kitchen.ws-group}")
public class KitchenBoardConsumer {

    private static final Logger log = LoggerFactory.getLogger(KitchenBoardConsumer.class);

    /** Where the kitchen display subscribes. */
    public static final String BOARD_TOPIC = "/topic/kitchen";

    private final KitchenAppService kitchenService;
    private final SimpMessagingTemplate messaging;

    public KitchenBoardConsumer(KitchenAppService kitchenService, SimpMessagingTemplate messaging) {
        this.kitchenService = kitchenService;
        this.messaging = messaging;
    }

    @KafkaHandler
    public void on(OrderPlaced event) {
        kitchenService.onOrderPlaced(event);
        broadcast("order %s placed".formatted(event.orderId()));
    }

    @KafkaHandler
    public void on(OrderStatusChanged event) {
        kitchenService.onStatusChanged(event.orderId(), event.status());
        broadcast("order %s -> %s".formatted(event.orderId(), event.status()));
    }

    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("Ignoring {} on {}", event.getClass().getSimpleName(), Topics.ORDERS);
    }

    /**
     * Sends the whole board rather than a delta.
     *
     * <p>A few dozen tickets at most, and it makes every frame self-contained: a display
     * that reconnects mid-service is immediately correct instead of replaying a delta
     * stream it may have gaps in.
     */
    private void broadcast(String reason) {
        messaging.convertAndSend(BOARD_TOPIC, kitchenService.board());
        log.debug("Pushed board to {} ({})", BOARD_TOPIC, reason);
    }
}
