package com.restaurant.ordering.table.api;

import java.util.List;

import com.restaurant.ordering.security.Claims;
import com.restaurant.ordering.security.Roles;
import com.restaurant.ordering.table.api.TableDtos.AttentionRequest;
import com.restaurant.ordering.table.api.TableDtos.ScanRequest;
import com.restaurant.ordering.table.api.TableDtos.SessionResponse;
import com.restaurant.ordering.table.api.TableDtos.StateRequest;
import com.restaurant.ordering.table.api.TableDtos.TableView;
import com.restaurant.ordering.table.app.TableAppService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableAppService tableService;

    public TableController(TableAppService tableService) {
        this.tableService = tableService;
    }

    /**
     * The QR scan. Unauthenticated by necessity — this is where a customer gets their
     * first credential, so there is nothing to present yet.
     *
     * <p>The only thing standing between a stranger and a table session is the
     * unguessability of the QR value itself, which is why the seeded codes are random
     * rather than derived from the table code.
     */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse scan(@Valid @RequestBody ScanRequest request) {
        return tableService.scan(request.qrCode());
    }

    /** Staff dashboard: every table at a glance. */
    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.STAFF + "','" + Roles.MANAGER + "','" + Roles.KITCHEN + "')")
    public List<TableView> list() {
        return tableService.listTables();
    }

    /** A customer reading their own table. Staff may read any. */
    @GetMapping("/{tableId}")
    public TableView get(@PathVariable Long tableId, @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, tableId);
        return tableService.get(tableId);
    }

    /**
     * Flag or clear "we need someone over here".
     *
     * <p>Both roles use this endpoint: a diner raises it from their phone, a waiter clears
     * it from the dashboard. A customer may only touch their own table.
     */
    @PostMapping("/{tableId}/attention")
    public TableView attention(@PathVariable Long tableId,
                               @Valid @RequestBody AttentionRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, tableId);
        return tableService.setAttention(tableId, request.flagged(), request.note());
    }

    /** Staff-only: seat, settle, or clear a table. */
    @PostMapping("/{tableId}/state")
    @PreAuthorize("hasAnyRole('" + Roles.STAFF + "','" + Roles.MANAGER + "')")
    public TableView state(@PathVariable Long tableId, @Valid @RequestBody StateRequest request) {
        return tableService.setState(tableId, request.state());
    }

    /**
     * Customer tokens carry the table they were issued for. Comparing against that claim —
     * rather than trusting the path variable — is what stops a diner at table 3 reading or
     * flagging table 4 by editing the URL.
     */
    private void requireStaffOrOwnTable(Jwt jwt, Long tableId) {
        String role = jwt.getClaimAsString(Claims.ROLE);
        if (!Roles.CUSTOMER.equals(role)) {
            return;
        }
        Object claim = jwt.getClaim(Claims.TABLE_ID);
        Long ownTableId = claim instanceof Number number ? number.longValue() : null;
        if (!tableId.equals(ownTableId)) {
            throw new AccessDeniedException("Session is scoped to table " + ownTableId);
        }
    }
}
