package com.restaurant.ordering.order.app;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the menu from menu-service at order time.
 *
 * <p>Prices and availability are re-read here rather than trusted from the request body.
 * The customer's phone holds a menu that may be minutes old and, more importantly, is
 * client-controlled: taking the price from the client would let anyone order a steak for a
 * penny. menu-service answers from its Redis cache, so this stays a fast call.
 */
@Component
public class MenuClient {

    private static final Logger log = LoggerFactory.getLogger(MenuClient.class);

    private final RestClient restClient;

    public MenuClient(RestClient.Builder builder, MenuServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * @param bearerToken the caller's own token, forwarded so menu-service can authorise
     *                    the read. There is no service account: the customer placing the
     *                    order already has the right to read the menu.
     */
    public ItemSnapshot fetchItem(Long menuItemId, String bearerToken) {
        try {
            ItemSnapshot snapshot = restClient.get()
                    .uri("/api/menu/items/{id}/availability", menuItemId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(ItemSnapshot.class);
            if (snapshot == null) {
                throw new MenuUnavailableException("menu-service returned no body for item " + menuItemId);
            }
            return snapshot;
        } catch (RestClientException ex) {
            log.warn("menu-service lookup failed for item {}", menuItemId, ex);
            // Deliberately not falling back to the client's prices: an unreachable menu
            // means the order cannot be priced or validated, and accepting it anyway would
            // be worse than rejecting it.
            throw new MenuUnavailableException("Could not reach menu-service for item " + menuItemId, ex);
        }
    }

    public MenuSnapshot fetchMenu(String bearerToken) {
        MenuSnapshot menu = restClient.get()
                .uri("/api/menu")
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .body(MenuSnapshot.class);
        if (menu == null) {
            throw new MenuUnavailableException("menu-service returned no menu");
        }
        return menu;
    }

    /** Mirrors menu-service's AvailabilityView. */
    public record ItemSnapshot(Long menuItemId, String name, boolean available, long priceCents) {
    }

    public record MenuSnapshot(List<CategorySnapshot> categories) {
    }

    public record CategorySnapshot(Long id, String name, List<ItemDetail> items) {
    }

    public record ItemDetail(
            Long id,
            String name,
            long priceCents,
            boolean available,
            List<ModifierGroupDetail> modifierGroups) {
    }

    public record ModifierGroupDetail(
            Long id,
            String name,
            int minSelect,
            int maxSelect,
            List<ModifierDetail> modifiers) {
    }

    public record ModifierDetail(Long id, String name, long priceDeltaCents, boolean available) {
    }
}
