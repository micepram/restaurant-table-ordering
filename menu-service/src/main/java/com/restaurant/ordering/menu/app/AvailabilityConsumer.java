package com.restaurant.ordering.menu.app;

import com.restaurant.ordering.events.ItemAvailabilityChanged;
import com.restaurant.ordering.events.Topics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies availability changes announced by the kitchen.
 *
 * <p>This is the first half of the "we just ran out of salmon" path:
 *
 * <pre>
 * kitchen board 86s an item
 *   -> kitchen-service publishes ItemAvailabilityChanged  (menu.availability)
 *      -> here: menu-service updates the row, evicts Redis, publishes MenuInvalidated
 *         -> notification-service pushes to every open table session
 * </pre>
 *
 * <p>menu-service owns menu state, so the kitchen announces an intent and this service
 * decides what the menu actually says — the same single-writer rule that governs order
 * status. It also means the write and the cache eviction happen in one place and cannot
 * drift apart.
 *
 * <p>Class-level {@code @KafkaListener} with {@code @KafkaHandler} methods lets one topic
 * carry several event types: Spring dispatches on the payload type resolved from the
 * {@code __TypeId__} header. {@link com.restaurant.ordering.events.MenuInvalidated} also
 * lands on this topic — it is this service's own output, so it is deliberately ignored
 * rather than re-applied.
 */
@Component
@KafkaListener(topics = Topics.MENU_AVAILABILITY, groupId = "menu-service")
public class AvailabilityConsumer {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityConsumer.class);

    private final MenuAppService menuService;

    public AvailabilityConsumer(MenuAppService menuService) {
        this.menuService = menuService;
    }

    @KafkaHandler
    public void on(ItemAvailabilityChanged event) {
        log.info("Kitchen set item {} available={} ({})",
                event.menuItemId(), event.available(), event.reason());
        try {
            menuService.setAvailability(
                    event.menuItemId(), event.available(), event.reason(), event.changedBy());
        } catch (MenuItemNotFoundException ex) {
            // A change for an item that no longer exists cannot be applied and will never
            // succeed on retry, so it must not be redelivered forever.
            log.warn("Ignoring availability change for unknown item {}", event.menuItemId());
        }
    }

    /**
     * Catch-all so this service's own {@code MenuInvalidated} messages, and any future
     * event type on the topic, do not blow up the listener. Without it Spring rejects the
     * unmatched payload and the container retries the same record indefinitely.
     */
    @KafkaHandler(isDefault = true)
    public void onOther(Object event) {
        log.trace("Ignoring {} on {}", event.getClass().getSimpleName(), Topics.MENU_AVAILABILITY);
    }
}
