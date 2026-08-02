package com.restaurant.ordering.table.domain;

import java.time.Instant;

import com.restaurant.ordering.events.TableState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "restaurant_table")
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TableState state = TableState.FREE;

    @Column(name = "attention_flagged", nullable = false)
    private boolean attentionFlagged;

    @Column(name = "attention_note")
    private String attentionNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RestaurantTable() {
        // for JPA
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Clearing a table ends the seating: state returns to FREE and any outstanding
     * attention flag is dropped, since it belonged to the party that just left.
     */
    public void clear() {
        this.state = TableState.FREE;
        this.attentionFlagged = false;
        this.attentionNote = null;
    }

    public void flagForAttention(String note) {
        this.attentionFlagged = true;
        this.attentionNote = note;
    }

    public void resolveAttention() {
        this.attentionFlagged = false;
        this.attentionNote = null;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getQrCode() {
        return qrCode;
    }

    public int getSeats() {
        return seats;
    }

    public TableState getState() {
        return state;
    }

    public void setState(TableState state) {
        this.state = state;
    }

    public boolean isAttentionFlagged() {
        return attentionFlagged;
    }

    public String getAttentionNote() {
        return attentionNote;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
