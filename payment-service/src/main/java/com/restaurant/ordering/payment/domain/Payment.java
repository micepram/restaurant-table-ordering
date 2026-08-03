package com.restaurant.ordering.payment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One attempt to pay part or all of a bill.
 *
 * <p>Declines are recorded, not discarded: "the card was declined twice before it worked"
 * is exactly the history a staff member needs when a customer disputes a charge.
 */
@Entity
@Table(name = "payment")
public class Payment {

    public enum Status {
        APPROVED,
        DECLINED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "tip_cents", nullable = false)
    private long tipCents;

    /** Only the last four digits are stored; the rest never reaches the database. */
    @Column(name = "card_last4")
    private String cardLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Payment() {
        // for JPA
    }

    public static Payment of(Long orderId,
                             long amountCents,
                             long tipCents,
                             String cardLast4,
                             Status status,
                             String failureReason,
                             String reference) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.amountCents = amountCents;
        payment.tipCents = tipCents;
        payment.cardLast4 = cardLast4;
        payment.status = status;
        payment.failureReason = failureReason;
        payment.reference = reference;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getTipCents() {
        return tipCents;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
