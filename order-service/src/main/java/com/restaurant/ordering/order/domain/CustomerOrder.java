package com.restaurant.ordering.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.restaurant.ordering.events.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The order aggregate. This class is the <em>only</em> place order status changes.
 *
 * <p>The kitchen and payment-service both want to move an order along, but neither writes
 * status directly: they publish an intent, order-service consumes it, and
 * {@link #advanceTo} decides whether the move is legal. Without that single-writer rule the
 * kitchen's view and the order's own view drift apart under concurrency, and there is no
 * authority to reconcile them.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_code", nullable = false)
    private String tableCode;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    /**
     * Optimistic lock. Two kitchen terminals advancing the same ticket at once become a
     * detectable conflict rather than a lost update.
     */
    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("sortOrder ASC")
    private List<OrderLineEntity> lines = new ArrayList<>();

    protected CustomerOrder() {
        // for JPA
    }

    public static CustomerOrder place(Long tableId, String tableCode, UUID sessionId, List<OrderLineEntity> lines) {
        CustomerOrder order = new CustomerOrder();
        order.tableId = tableId;
        order.tableCode = tableCode;
        order.sessionId = sessionId;
        order.status = OrderStatus.PLACED;
        order.placedAt = Instant.now();
        order.lines.addAll(lines);
        order.subtotalCents = lines.stream().mapToLong(OrderLineEntity::getLineTotalCents).sum();
        return order;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a status transition after checking it against the state machine.
     *
     * @throws IllegalOrderTransitionException if the move is not legal from the current
     *         status. Callers driven by Kafka treat this as a message to drop, not retry:
     *         a duplicate or out-of-order intent will never become legal on redelivery.
     */
    public void advanceTo(OrderStatus next) {
        if (status == next) {
            throw new IllegalOrderTransitionException(id, status, next, "already in that state");
        }
        if (!status.canTransitionTo(next)) {
            throw new IllegalOrderTransitionException(id, status, next, "not a permitted transition");
        }
        this.status = next;
    }

    public boolean canTransitionTo(OrderStatus next) {
        return status != next && status.canTransitionTo(next);
    }

    public Long getId() {
        return id;
    }

    public Long getTableId() {
        return tableId;
    }

    public String getTableCode() {
        return tableCode;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public List<OrderLineEntity> getLines() {
        return lines;
    }
}
