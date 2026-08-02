package com.restaurant.ordering.table.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.restaurant.ordering.events.TableEventChanged;
import com.restaurant.ordering.events.TableState;
import com.restaurant.ordering.events.Topics;
import com.restaurant.ordering.kafka.EventPublisher;
import com.restaurant.ordering.security.JwtProperties;
import com.restaurant.ordering.security.TokenIssuer;
import com.restaurant.ordering.table.api.TableDtos.SessionResponse;
import com.restaurant.ordering.table.api.TableDtos.TableView;
import com.restaurant.ordering.table.domain.RestaurantTable;
import com.restaurant.ordering.table.domain.RestaurantTableRepository;
import com.restaurant.ordering.table.domain.TableSession;
import com.restaurant.ordering.table.domain.TableSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableAppService {

    private final RestaurantTableRepository tables;
    private final TableSessionRepository sessions;
    private final TokenIssuer tokenIssuer;
    private final JwtProperties jwtProperties;
    private final EventPublisher events;

    public TableAppService(RestaurantTableRepository tables,
                           TableSessionRepository sessions,
                           TokenIssuer tokenIssuer,
                           JwtProperties jwtProperties,
                           EventPublisher events) {
        this.tables = tables;
        this.sessions = sessions;
        this.tokenIssuer = tokenIssuer;
        this.jwtProperties = jwtProperties;
        this.events = events;
    }

    /**
     * Handles a QR scan: resolves the sticker to a table, joins or opens the seating, and
     * mints a session token scoped to that table.
     *
     * <p>Rejoining an open session rather than replacing it is deliberate — several phones
     * at one table must share one bill.
     */
    @Transactional
    public SessionResponse scan(String qrCode) {
        RestaurantTable table = tables.findByQrCode(qrCode)
                .orElseThrow(() -> new TableNotFoundException("No table for QR code " + qrCode));

        TableSession session = sessions.findByTableIdAndEndedAtIsNull(table.getId())
                .orElseGet(() -> sessions.save(TableSession.open(table.getId())));

        if (table.getState() == TableState.FREE) {
            table.setState(TableState.SEATED);
            publishAfterCommit(table, null);
        }

        String token = tokenIssuer.issueCustomerToken(table.getId(), table.getCode(), session.getId());
        return new SessionResponse(
                token,
                table.getId(),
                table.getCode(),
                session.getId(),
                Instant.now().plus(jwtProperties.customerTtl()));
    }

    @Transactional(readOnly = true)
    public List<TableView> listTables() {
        return tables.findAllByOrderByCodeAsc().stream().map(TableView::of).toList();
    }

    @Transactional(readOnly = true)
    public TableView get(Long tableId) {
        return TableView.of(require(tableId));
    }

    /**
     * Raises or clears the "someone come over here" flag. Customers may only flag their own
     * table; the controller enforces that against the token's tableId claim.
     */
    @Transactional
    public TableView setAttention(Long tableId, boolean flagged, String note) {
        RestaurantTable table = require(tableId);
        if (flagged) {
            table.flagForAttention(note);
        } else {
            table.resolveAttention();
        }
        publishAfterCommit(table, note);
        return TableView.of(table);
    }

    @Transactional
    public TableView setState(Long tableId, TableState state) {
        RestaurantTable table = require(tableId);
        if (state == TableState.FREE) {
            table.clear();
            closeOpenSession(table.getId());
        } else {
            table.setState(state);
        }
        publishAfterCommit(table, null);
        return TableView.of(table);
    }

    private void closeOpenSession(Long tableId) {
        sessions.findByTableIdAndEndedAtIsNull(tableId).ifPresent(TableSession::close);
    }

    private RestaurantTable require(Long tableId) {
        return tables.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("No table " + tableId));
    }

    private void publishAfterCommit(RestaurantTable table, String note) {
        events.publishAfterCommit(Topics.TABLES, new TableEventChanged(
                UUID.randomUUID(),
                Instant.now(),
                table.getId(),
                table.getCode(),
                table.getState(),
                table.isAttentionFlagged(),
                note));
    }
}
