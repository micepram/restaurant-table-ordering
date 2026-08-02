package com.restaurant.ordering.menu.api;

import java.util.List;

import com.restaurant.ordering.menu.api.MenuViews.AvailabilityView;
import com.restaurant.ordering.menu.api.MenuViews.MenuView;
import com.restaurant.ordering.menu.app.MenuAppService;
import com.restaurant.ordering.security.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuAppService menuService;

    public MenuController(MenuAppService menuService) {
        this.menuService = menuService;
    }

    /**
     * The full menu. Any authenticated principal may read it — a diner with a table session
     * as much as staff.
     */
    @GetMapping
    public MenuView menu() {
        return menuService.getFullMenu();
    }

    /** Point availability check, used by order-service before accepting a line. */
    @GetMapping("/items/{menuItemId}/availability")
    public AvailabilityView availability(@PathVariable Long menuItemId) {
        return menuService.getAvailability(menuItemId);
    }

    /** Availability across the menu, for the kitchen's 86 board. */
    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('" + Roles.KITCHEN + "','" + Roles.STAFF + "','" + Roles.MANAGER + "')")
    public List<AvailabilityView> allAvailability() {
        return menuService.listAvailability();
    }

    /**
     * Direct availability toggle for the kitchen and managers.
     *
     * <p>The kitchen display normally goes through Kafka instead, so that one code path
     * updates the menu whichever surface triggered it. This endpoint exists for staff tools
     * and for driving the flow without a broker in the loop.
     *
     * @return 200 when the flag changed, 204 when it was already in that state
     */
    @PostMapping("/items/{menuItemId}/availability")
    @PreAuthorize("hasAnyRole('" + Roles.KITCHEN + "','" + Roles.MANAGER + "')")
    public ResponseEntity<AvailabilityView> setAvailability(
            @PathVariable Long menuItemId,
            @Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        boolean changed = menuService.setAvailability(
                menuItemId, request.available(), request.reason(), jwt.getSubject());

        return changed
                ? ResponseEntity.ok(menuService.getAvailability(menuItemId))
                : ResponseEntity.noContent().build();
    }

    public record AvailabilityRequest(
            @NotNull Boolean available,
            @Size(max = 255) String reason) {
    }
}
