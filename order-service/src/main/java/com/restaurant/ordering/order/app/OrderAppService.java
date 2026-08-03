package com.restaurant.ordering.order.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.restaurant.ordering.events.OrderLine;
import com.restaurant.ordering.events.OrderPlaced;
import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.events.OrderStatusChanged;
import com.restaurant.ordering.events.Topics;
import com.restaurant.ordering.kafka.EventPublisher;
import com.restaurant.ordering.order.api.OrderDtos.LineRequest;
import com.restaurant.ordering.order.api.OrderDtos.OrderView;
import com.restaurant.ordering.order.api.OrderDtos.PlaceOrderRequest;
import com.restaurant.ordering.order.app.MenuClient.ItemDetail;
import com.restaurant.ordering.order.app.MenuClient.MenuSnapshot;
import com.restaurant.ordering.order.app.MenuClient.ModifierDetail;
import com.restaurant.ordering.order.app.MenuClient.ModifierGroupDetail;
import com.restaurant.ordering.order.domain.CustomerOrder;
import com.restaurant.ordering.order.domain.CustomerOrderRepository;
import com.restaurant.ordering.order.domain.OrderLineEntity;
import com.restaurant.ordering.order.domain.OrderLineModifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the order aggregate and is the single writer of order status.
 *
 * <p>Kitchen terminals and payment-service express intents on Kafka; this service decides
 * whether each one is legal and publishes the resulting {@link OrderStatusChanged}, which
 * everything downstream treats as truth.
 */
@Service
public class OrderAppService {

    private static final Logger log = LoggerFactory.getLogger(OrderAppService.class);

    private final CustomerOrderRepository orders;
    private final MenuClient menuClient;
    private final EventPublisher publisher;

    public OrderAppService(CustomerOrderRepository orders, MenuClient menuClient, EventPublisher publisher) {
        this.orders = orders;
        this.menuClient = menuClient;
        this.publisher = publisher;
    }

    /**
     * Places an order, pricing and validating every line against menu-service.
     *
     * <p>The availability re-check happens here rather than relying on what the customer's
     * phone last saw. A cart can sit open for minutes while the kitchen 86s an item, and
     * the cached menu on the device is exactly the stale read this guards against.
     */
    @Transactional
    public OrderView place(Long tableId,
                           String tableCode,
                           UUID sessionId,
                           PlaceOrderRequest request,
                           String bearerToken) {

        MenuSnapshot menu = menuClient.fetchMenu(bearerToken);
        Map<Long, ItemDetail> itemsById = new HashMap<>();
        menu.categories().forEach(category -> category.items().forEach(item -> itemsById.put(item.id(), item)));

        List<OrderLineEntity> lines = new ArrayList<>();
        int sortOrder = 0;
        for (LineRequest lineRequest : request.lines()) {
            lines.add(buildLine(lineRequest, itemsById, sortOrder++));
        }

        CustomerOrder order = orders.save(CustomerOrder.place(tableId, tableCode, sessionId, lines));
        log.info("Order {} placed for table {} ({} lines, subtotal {})",
                order.getId(), tableCode, lines.size(), order.getSubtotalCents());

        publisher.publishAfterCommit(Topics.ORDERS, new OrderPlaced(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                order.getTableId(),
                order.getTableCode(),
                order.getPlacedAt(),
                order.getLines().stream().map(OrderAppService::toEventLine).toList(),
                order.getSubtotalCents()));

        return OrderView.of(order);
    }

    private OrderLineEntity buildLine(LineRequest request, Map<Long, ItemDetail> itemsById, int sortOrder) {
        ItemDetail item = itemsById.get(request.menuItemId());
        if (item == null) {
            throw new InvalidOrderException("Unknown menu item " + request.menuItemId());
        }
        if (!item.available()) {
            throw new InvalidOrderException("'" + item.name() + "' is no longer available");
        }

        Map<Long, ModifierDetail> allowed = new HashMap<>();
        Map<Long, Long> groupOfModifier = new HashMap<>();
        for (ModifierGroupDetail group : item.modifierGroups()) {
            for (ModifierDetail modifier : group.modifiers()) {
                allowed.put(modifier.id(), modifier);
                groupOfModifier.put(modifier.id(), group.id());
            }
        }

        List<OrderLineModifier> chosen = new ArrayList<>();
        Map<Long, Integer> perGroup = new HashMap<>();
        for (Long modifierId : request.modifierIds()) {
            ModifierDetail modifier = allowed.get(modifierId);
            if (modifier == null) {
                throw new InvalidOrderException(
                        "Modifier " + modifierId + " does not belong to '" + item.name() + "'");
            }
            if (!modifier.available()) {
                throw new InvalidOrderException("'" + modifier.name() + "' is no longer available");
            }
            chosen.add(OrderLineModifier.of(modifier.id(), modifier.name(), modifier.priceDeltaCents()));
            perGroup.merge(groupOfModifier.get(modifierId), 1, Integer::sum);
        }

        // Enforce each group's min/max. A mandatory group (minSelect >= 1) is how the menu
        // says the kitchen cannot start without a choice — steak doneness, for instance.
        for (ModifierGroupDetail group : item.modifierGroups()) {
            int count = perGroup.getOrDefault(group.id(), 0);
            if (count < group.minSelect()) {
                throw new InvalidOrderException(
                        "'%s' requires at least %d choice from '%s'"
                                .formatted(item.name(), group.minSelect(), group.name()));
            }
            if (count > group.maxSelect()) {
                throw new InvalidOrderException(
                        "'%s' allows at most %d from '%s'"
                                .formatted(item.name(), group.maxSelect(), group.name()));
            }
        }

        return OrderLineEntity.of(
                item.id(), item.name(), request.quantity(), item.priceCents(), chosen, request.note(), sortOrder);
    }

    /**
     * Applies a status transition. Every path that changes status funnels through here.
     *
     * @return true if applied, false if the transition was not legal. Kafka-driven callers
     *         use the boolean to drop a duplicate or out-of-order intent instead of
     *         retrying it forever.
     */
    @Transactional
    public boolean advance(Long orderId, OrderStatus next, String actor) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));

        OrderStatus previous = order.getStatus();
        if (!order.canTransitionTo(next)) {
            log.warn("Refusing {} -> {} for order {} requested by {}", previous, next, orderId, actor);
            return false;
        }

        order.advanceTo(next);
        log.info("Order {} {} -> {} (by {})", orderId, previous, next, actor);

        publisher.publishAfterCommit(Topics.ORDERS, new OrderStatusChanged(
                UUID.randomUUID(), Instant.now(), orderId, order.getTableId(), previous, next));
        return true;
    }

    /** Same as {@link #advance} but surfaces the refusal as a 409 for HTTP callers. */
    @Transactional
    public OrderView advanceOrThrow(Long orderId, OrderStatus next, String actor) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));
        OrderStatus previous = order.getStatus();
        order.advanceTo(next);
        log.info("Order {} {} -> {} (by {})", orderId, previous, next, actor);

        publisher.publishAfterCommit(Topics.ORDERS, new OrderStatusChanged(
                UUID.randomUUID(), Instant.now(), orderId, order.getTableId(), previous, next));
        return OrderView.of(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(Long orderId) {
        return orders.findByIdWithLines(orderId)
                .map(OrderView::of)
                .orElseThrow(() -> new OrderNotFoundException("No order " + orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderView> forTable(Long tableId) {
        return orders.findByTableIdOrderByPlacedAtDesc(tableId).stream().map(OrderView::of).toList();
    }

    /** Everything still in the kitchen's hands, oldest first. */
    @Transactional(readOnly = true)
    public List<OrderView> openOrders() {
        return orders.findByStatusInOrderByPlacedAtAsc(
                        List.of(OrderStatus.PLACED, OrderStatus.ACKNOWLEDGED, OrderStatus.PREPARING, OrderStatus.READY))
                .stream()
                .map(OrderView::of)
                .toList();
    }

    private static OrderLine toEventLine(OrderLineEntity line) {
        return new OrderLine(
                line.getMenuItemId(),
                line.getName(),
                line.getQuantity(),
                line.getUnitPriceCents(),
                line.getModifiers().stream().map(OrderLineModifier::getName).toList(),
                line.getModifiersTotalCents(),
                line.getLineTotalCents(),
                line.getNote());
    }
}
