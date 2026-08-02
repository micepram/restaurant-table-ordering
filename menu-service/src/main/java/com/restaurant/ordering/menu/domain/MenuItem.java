package com.restaurant.ordering.menu.domain;

import java.time.Instant;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_item")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    /**
     * The 86 flag. Flipped by the kitchen mid-service, which is what triggers the menu
     * cache eviction and the push to every open table session.
     */
    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ModifierGroup> modifierGroups = new ArrayList<>();

    protected MenuItem() {
        // for JPA
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** @return true if this actually changed the flag, so callers can skip a no-op event. */
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

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ModifierGroup> getModifierGroups() {
        return modifierGroups;
    }
}
