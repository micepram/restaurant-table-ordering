package com.restaurant.ordering.menu.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.restaurant.ordering.menu.domain.Category;
import com.restaurant.ordering.menu.domain.MenuItem;
import com.restaurant.ordering.menu.domain.Modifier;
import com.restaurant.ordering.menu.domain.ModifierGroup;

/**
 * The cached read model.
 *
 * <p>These are detached records rather than entities on purpose: the value is serialised
 * into Redis, and caching entities would drag lazy proxies and a persistence context into
 * the cache. Assembling the whole menu once and storing it as one value also means a table
 * session fetches the menu in a single round trip.
 *
 * <p>{@code Serializable} is implemented as a safety net only; the cache is configured for
 * JSON, so the JDK serialization path should never be taken.
 */
public final class MenuViews {

    private MenuViews() {
    }

    public record MenuView(Instant generatedAt, List<CategoryView> categories) implements Serializable {
    }

    public record CategoryView(Long id, String name, int sortOrder, List<MenuItemView> items)
            implements Serializable {

        public static CategoryView of(Category category, List<MenuItemView> items) {
            return new CategoryView(category.getId(), category.getName(), category.getSortOrder(), items);
        }
    }

    public record MenuItemView(
            Long id,
            String name,
            String description,
            long priceCents,
            boolean available,
            List<ModifierGroupView> modifierGroups) implements Serializable {

        public static MenuItemView of(MenuItem item, List<ModifierGroupView> groups) {
            return new MenuItemView(
                    item.getId(),
                    item.getName(),
                    item.getDescription(),
                    item.getPriceCents(),
                    item.isAvailable(),
                    groups);
        }
    }

    public record ModifierGroupView(
            Long id,
            String name,
            int minSelect,
            int maxSelect,
            List<ModifierView> modifiers) implements Serializable {

        public static ModifierGroupView of(ModifierGroup group, List<ModifierView> modifiers) {
            return new ModifierGroupView(
                    group.getId(),
                    group.getName(),
                    group.getMinSelect(),
                    group.getMaxSelect(),
                    modifiers);
        }
    }

    public record ModifierView(Long id, String name, long priceDeltaCents, boolean available)
            implements Serializable {

        public static ModifierView of(Modifier modifier) {
            return new ModifierView(
                    modifier.getId(),
                    modifier.getName(),
                    modifier.getPriceDeltaCents(),
                    modifier.isAvailable());
        }
    }

    /** Answer to "can this item still be ordered?", used by order-service at placement time. */
    public record AvailabilityView(Long menuItemId, String name, boolean available, long priceCents)
            implements Serializable {
    }
}
