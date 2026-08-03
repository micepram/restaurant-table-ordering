package com.restaurant.ordering.order.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * One line of an order.
 *
 * <p>The item name and prices are copies taken at placement time, not references to the
 * menu. If the kitchen re-prices or 86s an item afterwards, this line must still show what
 * the customer ordered at the price they agreed to.
 */
@Entity
@Table(name = "order_line")
public class OrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(name = "modifiers_total_cents", nullable = false)
    private long modifiersTotalCents;

    @Column(name = "line_total_cents", nullable = false)
    private long lineTotalCents;

    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_line_id", nullable = false)
    private List<OrderLineModifier> modifiers = new ArrayList<>();

    protected OrderLineEntity() {
        // for JPA
    }

    /**
     * @param unitPriceCents item price excluding modifiers
     * @param modifiers      chosen modifiers, already priced
     */
    public static OrderLineEntity of(Long menuItemId,
                                     String name,
                                     int quantity,
                                     long unitPriceCents,
                                     List<OrderLineModifier> modifiers,
                                     String note,
                                     int sortOrder) {
        OrderLineEntity line = new OrderLineEntity();
        line.menuItemId = menuItemId;
        line.name = name;
        line.quantity = quantity;
        line.unitPriceCents = unitPriceCents;
        line.modifiers.addAll(modifiers);
        line.modifiersTotalCents = modifiers.stream().mapToLong(OrderLineModifier::getPriceDeltaCents).sum();
        // Modifiers are per unit, so they scale with quantity along with the base price.
        line.lineTotalCents = (unitPriceCents + line.modifiersTotalCents) * quantity;
        line.note = note;
        line.sortOrder = sortOrder;
        return line;
    }

    public Long getId() {
        return id;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public long getModifiersTotalCents() {
        return modifiersTotalCents;
    }

    public long getLineTotalCents() {
        return lineTotalCents;
    }

    public String getNote() {
        return note;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<OrderLineModifier> getModifiers() {
        return modifiers;
    }
}
