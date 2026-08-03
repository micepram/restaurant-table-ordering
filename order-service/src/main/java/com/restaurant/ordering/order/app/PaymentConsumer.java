package com.restaurant.ordering.order.app;

import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.events.PaymentCompleted;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Moves an order to PAID once its bill is fully settled.
 *
 * <p>A split bill emits one {@link PaymentCompleted} per payer, so the transition is driven
 * by {@link PaymentCompleted#settledInFull()} rather than by the arrival of any single
 * payment — otherwise the first of three diners to pay would close the whole order.
 */
@Component
@KafkaListener(topics = Topics.PAYMENTS, groupId = "order-service")
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final OrderAppService orderService;

    public PaymentConsumer(OrderAppService orderService) {
        this.orderService = orderService;
    }

    @KafkaHandler
    public void on(PaymentCompleted event) {
        if (!event.settledInFull()) {
            log.debug("Order {} part-paid, {} cents outstanding",
                    event.orderId(), event.outstandingCents());
            return;
        }
        try {
            boolean applied = orderService.advance(event.orderId(), OrderStatus.PAID, "payment-service");
            if (!applied) {
                log.debug("Order {} could not move to PAID from its current state", event.orderId());
            }
        } catch (OrderNotFoundException ex) {
            log.warn("Payment for unknown order {}", event.orderId());
        }
    }

    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("Ignoring {} on {}", event.getClass().getSimpleName(), Topics.PAYMENTS);
    }
}
