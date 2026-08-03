package com.restaurant.ordering.order.api;

import java.time.Instant;
import java.util.List;

import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.order.domain.CustomerOrder;
import com.restaurant.ordering.order.domain.OrderLineEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * What the customer's phone sends.
     *
     * <p>Notice there are no prices here. The client sends only what was chosen; every
     * price is re-read from menu-service, so a tampered request cannot set its own total.
     */
    public record PlaceOrderRequest(@NotEmpty @Valid List<LineRequest> lines) {
    }

    public record LineRequest(
            @NotNull Long menuItemId,
            @Min(1) int quantity,
            List<Long> modifierIds,
            @Size(max = 255) String note) {

        public LineRequest {
            modifierIds = modifierIds == null ? List.of() : List.copyOf(modifierIds);
        }
    }

    public record OrderView(
            Long id,
            Long tableId,
            String tableCode,
            OrderStatus status,
            List<OrderStatus> nextStates,
            Instant placedAt,
            Instant updatedAt,
            long subtotalCents,
            List<LineView> lines) {

        public static OrderView of(CustomerOrder order) {
            return new OrderView(
                    order.getId(),
                    order.getTableId(),
                    order.getTableCode(),
                    order.getStatus(),
                    List.copyOf(order.getStatus().nextStates()),
                    order.getPlacedAt(),
                    order.getUpdatedAt(),
                    order.getSubtotalCents(),
                    order.getLines().stream().map(LineView::of).toList());
        }
    }

    public record LineView(
            Long id,
            Long menuItemId,
            String name,
            int quantity,
            long unitPriceCents,
            long modifiersTotalCents,
            long lineTotalCents,
            String note,
            List<String> modifiers) {

        public static LineView of(OrderLineEntity line) {
            return new LineView(
                    line.getId(),
                    line.getMenuItemId(),
                    line.getName(),
                    line.getQuantity(),
                    line.getUnitPriceCents(),
                    line.getModifiersTotalCents(),
                    line.getLineTotalCents(),
                    line.getNote(),
                    line.getModifiers().stream().map(m -> m.getName()).toList());
        }
    }

    /** Staff or kitchen asking for a status move over HTTP rather than via Kafka. */
    public record AdvanceRequest(@NotNull OrderStatus status) {
    }
}
