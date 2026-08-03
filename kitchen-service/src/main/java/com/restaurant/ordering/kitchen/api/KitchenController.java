package com.restaurant.ordering.kitchen.api;

import com.restaurant.ordering.events.OrderStatus;
import com.restaurant.ordering.kitchen.api.KitchenViews.BoardUpdate;
import com.restaurant.ordering.kitchen.api.KitchenViews.TicketView;
import com.restaurant.ordering.kitchen.app.KitchenAppService;
import com.restaurant.ordering.security.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
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

/**
 * The kitchen display's HTTP surface.
 *
 * <p>Live updates arrive over WebSocket; these endpoints cover the initial load and the
 * cook's actions. A board that has just connected fetches {@code /board} once and then
 * relies on the socket.
 */
@RestController
@RequestMapping("/api/kitchen")
@PreAuthorize("hasAnyRole('" + Roles.KITCHEN + "','" + Roles.STAFF + "','" + Roles.MANAGER + "')")
public class KitchenController {

    private final KitchenAppService kitchenService;

    public KitchenController(KitchenAppService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /** Initial board load. Same payload the WebSocket pushes, so the client has one shape. */
    @GetMapping("/board")
    public BoardUpdate board() {
        return kitchenService.board();
    }

    @GetMapping("/tickets/{orderId}")
    public TicketView ticket(@PathVariable Long orderId) {
        return kitchenService.ticket(orderId);
    }

    /**
     * A cook advances a ticket.
     *
     * <p>Returns 202, not 200: this publishes an intent. order-service decides whether the
     * move is legal, and the board updates when the resulting status change comes back over
     * Kafka. Returning 200 with a ticket would imply the transition had already happened.
     */
    @PostMapping("/tickets/{orderId}/advance")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void advance(@PathVariable Long orderId,
                        @Valid @RequestBody AdvanceRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        TicketView ticket = kitchenService.ticket(orderId);
        kitchenService.requestAdvance(orderId, ticket.tableId(), request.status(), jwt.getSubject());
    }

    /**
     * 86 an item, or put it back on.
     *
     * <p>Also 202: this publishes to menu-service, which owns menu state and performs the
     * write, the Redis eviction, and the push to every open table session.
     */
    @PostMapping("/items/{menuItemId}/availability")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void setAvailability(@PathVariable Long menuItemId,
                                @Valid @RequestBody AvailabilityRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        kitchenService.requestAvailabilityChange(
                menuItemId, request.available(), request.reason(), jwt.getSubject());
    }

    public record AdvanceRequest(@NotNull OrderStatus status) {
    }

    public record AvailabilityRequest(
            @NotNull Boolean available,
            @Size(max = 255) String reason) {
    }
}
