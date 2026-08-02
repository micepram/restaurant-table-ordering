package com.restaurant.ordering.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "modifier")
public class Modifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @Column(nullable = false)
    private String name;

    /** Added to the item price. Signed, so a modifier could also discount. */
    @Column(name = "price_delta_cents", nullable = false)
    private long priceDeltaCents;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Modifier() {
        // for JPA
    }

    public boolean setAvailable(boolean available) {
        if (this.available == available) {
            return false;
        }
        this.available = available;
        return true;
    }

    public Long getId() {
        return id;
    }

    public ModifierGroup getModifierGroup() {
        return modifierGroup;
    }

    public String getName() {
        return name;
    }

    public long getPriceDeltaCents() {
        return priceDeltaCents;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
