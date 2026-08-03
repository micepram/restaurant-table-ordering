package com.restaurant.ordering.kitchen.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.restaurant.ordering.events.ItemAvailabilityChanged;
import com.restaurant.ordering.events.KitchenTicketAdvanced;
import com.restaurant.ordering.events.OrderPlaced;
import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.events.Topics;
import com.restaurant.ordering.kafka.EventPublisher;
import com.restaurant.ordering.kitchen.api.KitchenViews.BoardUpdate;
import com.restaurant.ordering.kitchen.api.KitchenViews.TicketView;
import com.restaurant.ordering.kitchen.config.KitchenProperties;
import com.restaurant.ordering.kitchen.domain.KitchenTicket;
import com.restaurant.ordering.kitchen.domain.KitchenTicketLine;
import com.restaurant.ordering.kitchen.domain.KitchenTicketRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenAppService {

    private static final Logger log = LoggerFactory.getLogger(KitchenAppService.class);

    /** Statuses the kitchen still owns. PAID and CANCELLED drop off the board. */
    private static final List<OrderStatus> ON_BOARD = List.of(
            OrderStatus.PLACED, OrderStatus.ACKNOWLEDGED, OrderStatus.PREPARING, OrderStatus.READY);

    private final KitchenTicketRepository tickets;
    private final EventPublisher publisher;
    private final KitchenProperties properties;

    public KitchenAppService(KitchenTicketRepository tickets,
                             EventPublisher publisher,
                             KitchenProperties properties) {
        this.tickets = tickets;
        this.publisher = publisher;
        this.properties = properties;
    }

    /**
     * Projects a newly placed order onto the board.
     *
     * <p>Keyed by order id and written with save(), so a redelivered {@code OrderPlaced}
     * overwrites the same row instead of producing a second ticket. Kafka is at-least-once,
     * so this will happen.
     */
    @Transactional
    public void onOrderPlaced(OrderPlaced event) {
        if (tickets.existsById(event.orderId())) {
            log.debug("Ticket {} already on the board, ignoring redelivery", event.orderId());
            return;
        }

        int[] sort = {0};
        List<KitchenTicketLine> lines = event.lines().stream()
                .map(line -> KitchenTicketLine.of(
                        line.name(),
                        line.quantity(),
                        line.modifiers().isEmpty() ? null : String.join(", ", line.modifiers()),
                        line.note(),
                        sort[0]++))
                .toList();

        tickets.save(KitchenTicket.from(
                event.orderId(),
                event.tableId(),
                event.tableCode(),
                event.placedAt(),
                event.subtotalCents(),
                lines));

        log.info("Ticket {} added to board (table {}, {} lines)",
                event.orderId(), event.tableCode(), lines.size());
    }

    /** Mirrors an authoritative status change from order-service. */
    @Transactional
    public void onStatusChanged(Long orderId, OrderStatus status) {
        KitchenTicket ticket = tickets.findById(orderId).orElse(null);
        if (ticket == null) {
            // Ordering across partitions is not guaranteed between different keys, and the
            // board is a projection: if the status arrived before the order that created
            // the ticket, the OrderPlaced replay will carry the current status anyway.
            log.debug("Status {} for unknown ticket {}, ignoring", status, orderId);
            return;
        }
        ticket.applyStatus(status);
        log.debug("Ticket {} now {}", orderId, status);
    }

    /**
     * A cook advanced a ticket.
     *
     * <p>Publishes an intent rather than writing status. order-service validates it and
     * publishes the resulting fact, which comes back here as a status change. The board
     * therefore only ever displays state that order-service has agreed to.
     */
    public void requestAdvance(Long orderId, Long tableId, OrderStatus requested, String requestedBy) {
        log.info("Kitchen requests {} for order {} (by {})", requested, orderId, requestedBy);
        publisher.publish(Topics.KITCHEN, new KitchenTicketAdvanced(
                UUID.randomUUID(), Instant.now(), orderId, tableId, requested, requestedBy));
    }

    /**
     * The kitchen 86s an item (or puts it back on).
     *
     * <p>Published to menu-service rather than written locally: menu-service owns menu
     * state, and routing through it is what makes the Redis eviction and the push to every
     * open table session happen exactly once, in one place.
     */
    public void requestAvailabilityChange(Long menuItemId, boolean available, String reason, String changedBy) {
        log.info("Kitchen sets item {} available={} ({})", menuItemId, available, reason);
        publisher.publish(Topics.MENU_AVAILABILITY, new ItemAvailabilityChanged(
                UUID.randomUUID(), Instant.now(), menuItemId, available, reason, changedBy));
    }

    @Transactional(readOnly = true)
    public BoardUpdate board() {
        Instant now = Instant.now();
        long warn = properties.warnAfter().getSeconds();
        long late = properties.lateAfter().getSeconds();
        List<TicketView> views = tickets.findBoard(ON_BOARD).stream()
                .map(ticket -> TicketView.of(ticket, now, warn, late))
                .toList();
        return new BoardUpdate(now, warn, late, views);
    }

    @Transactional(readOnly = true)
    public TicketView ticket(Long orderId) {
        KitchenTicket ticket = tickets.findWithLines(orderId);
        if (ticket == null) {
            throw new TicketNotFoundException("No ticket for order " + orderId);
        }
        return TicketView.of(ticket, Instant.now(),
                properties.warnAfter().getSeconds(), properties.lateAfter().getSeconds());
    }
}
