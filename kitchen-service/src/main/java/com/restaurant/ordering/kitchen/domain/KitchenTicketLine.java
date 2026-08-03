package com.restaurant.ordering.kitchen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line as the cook reads it.
 *
 * <p>Modifiers are flattened to a display string rather than kept as ids: the board is a
 * read surface, and a cook needs "Medium rare, Peppercorn" rather than a set of references
 * to resolve.
 */
@Entity
@Table(name = "kitchen_ticket_line")
public class KitchenTicketLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    private String modifiers;

    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected KitchenTicketLine() {
        // for JPA
    }

    public static KitchenTicketLine of(String name, int quantity, String modifiers, String note, int sortOrder) {
        KitchenTicketLine line = new KitchenTicketLine();
        line.name = name;
        line.quantity = quantity;
        line.modifiers = modifiers;
        line.note = note;
        line.sortOrder = sortOrder;
        return line;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getModifiers() {
        return modifiers;
    }

    public String getNote() {
        return note;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
