package com.restaurant.ordering.menu.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A choice attached to an item, e.g. "Cooked to" on a steak.
 *
 * <p>{@code minSelect >= 1} makes the group mandatory — order-service rejects an order line
 * that omits it, because the kitchen cannot start a steak without a doneness.
 */
@Entity
@Table(name = "modifier_group")
public class ModifierGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private String name;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select", nullable = false)
    private int maxSelect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "modifierGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Modifier> modifiers = new ArrayList<>();

    protected ModifierGroup() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public String getName() {
        return name;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public int getMaxSelect() {
        return maxSelect;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<Modifier> getModifiers() {
        return modifiers;
    }
}
