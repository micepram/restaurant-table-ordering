package com.restaurant.ordering.payment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The bill for one order.
 *
 * <p>Created lazily, snapshotting the order subtotal at that moment. Re-reading the order
 * on every payment would let the amount move underneath a part-paid split.
 */
@Entity
@Table(name = "bill")
public class Bill {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_code", nullable = false)
    private String tableCode;

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    @Column(name = "tip_cents", nullable = false)
    private long tipCents;

    @Column(name = "outstanding_cents", nullable = false)
    private long outstandingCents;

    @Column(nullable = false)
    private boolean settled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Split bills mean concurrent payers; this turns a race into a retry. */
    @Version
    private Long version;

    protected Bill() {
        // for JPA
    }

    public static Bill open(Long orderId, Long tableId, String tableCode, long subtotalCents) {
        Bill bill = new Bill();
        bill.orderId = orderId;
        bill.tableId = tableId;
        bill.tableCode = tableCode;
        bill.subtotalCents = subtotalCents;
        bill.tipCents = 0;
        bill.outstandingCents = subtotalCents;
        return bill;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Sets the tip for the whole bill and adds it to what is owed.
     *
     * @throws IllegalBillStateException if anything has already been paid — changing the tip
     *         after a part payment would silently re-price shares that have already settled
     */
    public void setTip(long newTipCents) {
        if (settled) {
            throw new IllegalBillStateException("Bill " + orderId + " is already settled");
        }
        if (outstandingCents != subtotalCents + tipCents) {
            throw new IllegalBillStateException(
                    "Cannot change the tip on bill " + orderId + " once part of it has been paid");
        }
        this.tipCents = newTipCents;
        this.outstandingCents = subtotalCents + newTipCents;
    }

    /**
     * Applies a successful payment.
     *
     * @throws IllegalBillStateException if the payment exceeds what is owed. Overpaying is
     *         rejected rather than accepted-and-refunded: the bill has no refund path, so an
     *         accepted overpayment would strand money with no way to return it.
     */
    public void applyPayment(long amountCents) {
        if (settled) {
            throw new IllegalBillStateException("Bill " + orderId + " is already settled");
        }
        if (amountCents > outstandingCents) {
            throw new IllegalBillStateException(
                    "Payment of %d exceeds the %d outstanding on bill %d"
                            .formatted(amountCents, outstandingCents, orderId));
        }
        this.outstandingCents -= amountCents;
        if (this.outstandingCents == 0) {
            this.settled = true;
        }
    }

    public long totalCents() {
        return subtotalCents + tipCents;
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

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getTipCents() {
        return tipCents;
    }

    public long getOutstandingCents() {
        return outstandingCents;
    }

    public boolean isSettled() {
        return settled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
