package com.restaurant.ordering.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A modifier chosen on an order line, with its price frozen at placement time. */
@Entity
@Table(name = "order_line_modifier")
public class OrderLineModifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "modifier_id", nullable = false)
    private Long modifierId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_delta_cents", nullable = false)
    private long priceDeltaCents;

    protected OrderLineModifier() {
        // for JPA
    }

    public static OrderLineModifier of(Long modifierId, String name, long priceDeltaCents) {
        OrderLineModifier modifier = new OrderLineModifier();
        modifier.modifierId = modifierId;
        modifier.name = name;
        modifier.priceDeltaCents = priceDeltaCents;
        return modifier;
    }

    public Long getId() {
        return id;
    }

    public Long getModifierId() {
        return modifierId;
    }

    public String getName() {
        return name;
    }

    public long getPriceDeltaCents() {
        return priceDeltaCents;
    }
}
