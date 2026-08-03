package com.restaurant.ordering.kitchen.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.restaurant.ordering.events.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A ticket on the kitchen board.
 *
 * <p>This is a projection of order-service's events, not an independent source of truth.
 * The kitchen never decides what an order's status is; it displays what order-service last
 * published and sends intents back. The id is the order's own id so that a redelivered
 * event upserts rather than duplicating.
 */
@Entity
@Table(name = "kitchen_ticket")
public class KitchenTicket {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_code", nullable = false)
    private String tableCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    /** Sort key for the board. The queue is strictly oldest-first across all tables. */
    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("sortOrder ASC")
    private List<KitchenTicketLine> lines = new ArrayList<>();

    protected KitchenTicket() {
        // for JPA
    }

    public static KitchenTicket from(Long orderId,
                                     Long tableId,
                                     String tableCode,
                                     Instant placedAt,
                                     long subtotalCents,
                                     List<KitchenTicketLine> lines) {
        KitchenTicket ticket = new KitchenTicket();
        ticket.orderId = orderId;
        ticket.tableId = tableId;
        ticket.tableCode = tableCode;
        ticket.placedAt = placedAt;
        ticket.subtotalCents = subtotalCents;
        ticket.status = OrderStatus.PLACED;
        ticket.lines.addAll(lines);
        return ticket;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Mirrors an authoritative status from order-service.
     *
     * <p>No validation here on purpose: order-service has already decided this is the
     * order's status, and a projection that second-guesses the source it projects would be
     * able to drift from it.
     */
    public void applyStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** How long this ticket has been waiting — what the board colours on. */
    public long waitSeconds(Instant now) {
        return Math.max(0, now.getEpochSecond() - placedAt.getEpochSecond());
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getTableId() {
        return tableId;
    }

    public String getTableCode() {
        return tableCode;
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

    public List<KitchenTicketLine> getLines() {
        return lines;
    }
}
