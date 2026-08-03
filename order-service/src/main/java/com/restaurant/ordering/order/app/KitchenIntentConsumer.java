package com.restaurant.ordering.order.app;

import com.restaurant.ordering.events.KitchenTicketAdvanced;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns kitchen intents into authoritative status changes.
 *
 * <p>The kitchen never writes order status. A cook pressing "start" publishes
 * {@link KitchenTicketAdvanced}; this consumer asks the aggregate whether that move is
 * legal and, if so, order-service publishes the {@code OrderStatusChanged} everyone else
 * believes. Two terminals double-tapping the same ticket therefore produce one transition
 * and one dropped duplicate, rather than two conflicting writes.
 */
@Component
@KafkaListener(topics = Topics.KITCHEN, groupId = "order-service")
public class KitchenIntentConsumer {

    private static final Logger log = LoggerFactory.getLogger(KitchenIntentConsumer.class);

    private final OrderAppService orderService;

    public KitchenIntentConsumer(OrderAppService orderService) {
        this.orderService = orderService;
    }

    @KafkaHandler
    public void on(KitchenTicketAdvanced event) {
        try {
            boolean applied = orderService.advance(
                    event.orderId(), event.requestedStatus(), event.requestedBy());
            if (!applied) {
                // Not an error: a redelivered or racing intent. Retrying would never make it
                // legal, so it is acknowledged and dropped.
                log.debug("Dropped kitchen intent {} for order {}",
                        event.requestedStatus(), event.orderId());
            }
        } catch (OrderNotFoundException ex) {
            log.warn("Kitchen intent for unknown order {}", event.orderId());
        }
    }

    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("Ignoring {} on {}", event.getClass().getSimpleName(), Topics.KITCHEN);
    }
}
