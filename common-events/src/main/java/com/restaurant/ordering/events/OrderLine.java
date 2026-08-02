package com.restaurant.ordering.events;

import java.util.List;

/**
 * One line on an order, denormalised for consumers.
 *
 * <p>Name and prices are copied in rather than referenced by id on purpose: the kitchen
 * ticket must keep showing what the customer actually ordered at the price they were
 * quoted, even if the menu changes underneath it.
 *
 * @param unitPriceCents price of the item itself, excluding modifiers
 * @param lineTotalCents {@code (unitPrice + sum(modifier prices)) * quantity}
 */
public record OrderLine(
        Long menuItemId,
        String name,
        int quantity,
        long unitPriceCents,
        List<String> modifiers,
        long modifiersTotalCents,
        long lineTotalCents,
        String note) {

    public OrderLine {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }
}
