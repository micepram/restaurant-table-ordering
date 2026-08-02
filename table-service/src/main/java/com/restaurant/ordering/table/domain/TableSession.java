package com.restaurant.ordering.table.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One seating at one table.
 *
 * <p>A partial unique index in the schema allows only one row per table with
 * {@code ended_at IS NULL}, so a second phone scanning the same sticker joins the open
 * session instead of starting a competing one — that is what lets two diners at one table
 * build a single shared bill.
 */
@Entity
@Table(name = "table_session")
public class TableSession {

    @Id
    private UUID id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected TableSession() {
        // for JPA
    }

    public static TableSession open(Long tableId) {
        TableSession session = new TableSession();
        session.id = UUID.randomUUID();
        session.tableId = tableId;
        session.startedAt = Instant.now();
        return session;
    }

    public void close() {
        this.endedAt = Instant.now();
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public Long getTableId() {
        return tableId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
