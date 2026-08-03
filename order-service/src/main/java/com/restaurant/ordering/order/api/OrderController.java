package com.restaurant.ordering.order.api;

import java.util.List;
import java.util.UUID;

import com.restaurant.ordering.order.api.OrderDtos.AdvanceRequest;
import com.restaurant.ordering.order.api.OrderDtos.OrderView;
import com.restaurant.ordering.order.api.OrderDtos.PlaceOrderRequest;
import com.restaurant.ordering.order.app.OrderAppService;
import com.restaurant.ordering.security.Claims;
import com.restaurant.ordering.security.Roles;

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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderAppService orderService;

    public OrderController(OrderAppService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order for the caller's own table.
     *
     * <p>The table, table code and session all come from the token, never from the body.
     * A customer cannot order onto someone else's table or bill, because there is no field
     * in the request that would let them try.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('" + Roles.CUSTOMER + "')")
    public OrderView place(@Valid @RequestBody PlaceOrderRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        Long tableId = requireTableId(jwt);
        String tableCode = jwt.getClaimAsString(Claims.TABLE_CODE);
        UUID sessionId = UUID.fromString(jwt.getClaimAsString(Claims.SESSION_ID));

        // The caller's own token is forwarded to menu-service for the price lookup.
        return orderService.place(tableId, tableCode, sessionId, request, jwt.getTokenValue());
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        OrderView order = orderService.get(orderId);
        requireStaffOrOwnTable(jwt, order.tableId());
        return order;
    }

    @GetMapping("/table/{tableId}")
    public List<OrderView> forTable(@PathVariable Long tableId, @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, tableId);
        return orderService.forTable(tableId);
    }

    /** Everything still open, oldest first — the kitchen queue and the staff dashboard. */
    @GetMapping("/open")
    @PreAuthorize("hasAnyRole('" + Roles.KITCHEN + "','" + Roles.STAFF + "','" + Roles.MANAGER + "')")
    public List<OrderView> open() {
        return orderService.openOrders();
    }

    /**
     * Direct status move for staff tooling.
     *
     * <p>The kitchen display drives status through Kafka instead, so that a cook's action
     * and this endpoint converge on the same single-writer path. Both end up in
     * {@code OrderAppService.advance}; only the trigger differs.
     */
    @PostMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('" + Roles.KITCHEN + "','" + Roles.STAFF + "','" + Roles.MANAGER + "')")
    public OrderView advance(@PathVariable Long orderId,
                             @Valid @RequestBody AdvanceRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return orderService.advanceOrThrow(orderId, request.status(), jwt.getSubject());
    }

    private Long requireTableId(Jwt jwt) {
        Object claim = jwt.getClaim(Claims.TABLE_ID);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Token carries no table scope");
    }

    private void requireStaffOrOwnTable(Jwt jwt, Long tableId) {
        if (!Roles.CUSTOMER.equals(jwt.getClaimAsString(Claims.ROLE))) {
            return;
        }
        Object claim = jwt.getClaim(Claims.TABLE_ID);
        Long ownTableId = claim instanceof Number number ? number.longValue() : null;
        if (!tableId.equals(ownTableId)) {
            throw new AccessDeniedException("Session is scoped to table " + ownTableId);
        }
    }
}
