package com.restaurant.ordering.menu.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.restaurant.ordering.events.MenuInvalidated;
import com.restaurant.ordering.events.Topics;
import com.restaurant.ordering.kafka.EventPublisher;
import com.restaurant.ordering.menu.api.MenuViews.AvailabilityView;
import com.restaurant.ordering.menu.api.MenuViews.CategoryView;
import com.restaurant.ordering.menu.api.MenuViews.MenuItemView;
import com.restaurant.ordering.menu.api.MenuViews.MenuView;
import com.restaurant.ordering.menu.api.MenuViews.ModifierGroupView;
import com.restaurant.ordering.menu.api.MenuViews.ModifierView;
import com.restaurant.ordering.menu.config.MenuCacheConfig;
import com.restaurant.ordering.menu.domain.Category;
import com.restaurant.ordering.menu.domain.MenuItem;
import com.restaurant.ordering.menu.domain.CategoryRepository;
import com.restaurant.ordering.menu.domain.MenuItemRepository;
import com.restaurant.ordering.menu.domain.ModifierGroupRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuAppService {

    private static final Logger log = LoggerFactory.getLogger(MenuAppService.class);

    /** Single key: the menu is small and always served whole. */
    static final String FULL_MENU_KEY = "full";

    private final CategoryRepository categories;
    private final MenuItemRepository items;
    private final ModifierGroupRepository groups;
    private final CacheManager cacheManager;
    private final EventPublisher publisher;

    public MenuAppService(CategoryRepository categories,
                          MenuItemRepository items,
                          ModifierGroupRepository groups,
                          CacheManager cacheManager,
                          EventPublisher publisher) {
        this.categories = categories;
        this.items = items;
        this.groups = groups;
        this.cacheManager = cacheManager;
        this.publisher = publisher;
    }

    /**
     * The whole menu, cached in Redis.
     *
     * <p>Assembled from three fetch queries in one transaction: categories with their items,
     * items with their modifier groups, groups with their modifiers. One combined query
     * would join two collections at once and fail with MultipleBagFetchException; three
     * separate ones populate the same object graph without N+1.
     */
    @Cacheable(cacheNames = MenuCacheConfig.MENU_CACHE, key = "'" + FULL_MENU_KEY + "'")
    @Transactional(readOnly = true)
    public MenuView getFullMenu() {
        log.debug("Assembling menu from database (cache miss)");

        List<Category> allCategories = categories.findAllWithItems();
        items.findAllWithGroups();
        Map<Long, List<ModifierGroupView>> groupsByItem = groups.findAllWithModifiers().stream()
                .collect(Collectors.groupingBy(
                        group -> group.getMenuItem().getId(),
                        Collectors.mapping(
                                group -> ModifierGroupView.of(group, group.getModifiers().stream()
                                        .map(ModifierView::of)
                                        .toList()),
                                Collectors.toList())));

        List<CategoryView> categoryViews = allCategories.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(category -> CategoryView.of(category, category.getItems().stream()
                        .map(item -> MenuItemView.of(item, groupsByItem.getOrDefault(item.getId(), List.of())))
                        .toList()))
                .toList();

        return new MenuView(Instant.now(), categoryViews);
    }

    /** Point lookup used by order-service to re-check an item at placement time. */
    @Cacheable(cacheNames = MenuCacheConfig.AVAILABILITY_CACHE, key = "#menuItemId")
    @Transactional(readOnly = true)
    public AvailabilityView getAvailability(Long menuItemId) {
        return items.findById(menuItemId)
                .map(item -> new AvailabilityView(item.getId(), item.getName(), item.isAvailable(), item.getPriceCents()))
                .orElseThrow(() -> new MenuItemNotFoundException("No menu item " + menuItemId));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> listAvailability() {
        return items.findAll().stream()
                .map(item -> new AvailabilityView(item.getId(), item.getName(), item.isAvailable(), item.getPriceCents()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    /**
     * Applies an availability change: the database write, the cache eviction, and the
     * downstream notification.
     *
     * <p>Eviction and publication are deferred to after commit rather than driven by
     * {@code @CacheEvict}. With the annotation, the eviction fires when the method returns
     * but before the transaction commits, leaving a window in which a concurrent reader
     * repopulates the cache from the old row and the fresh value is then committed behind
     * it — a stale menu that survives until the TTL. Ordering it after the commit closes
     * that window, and means any client reacting to {@link MenuInvalidated} is guaranteed
     * to read the new state.
     *
     * @return true if the flag actually changed; a no-op toggle publishes nothing
     */
    @Transactional
    public boolean setAvailability(Long menuItemId, boolean available, String reason, String changedBy) {
        MenuItem item = items.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException("No menu item " + menuItemId));

        if (!item.setAvailable(available)) {
            log.debug("Item {} already available={}, nothing to do", menuItemId, available);
            return false;
        }

        log.info("Item {} ({}) set available={} by {} ({})",
                item.getId(), item.getName(), available, changedBy, reason);

        String itemName = item.getName();
        AfterCommit.run(() -> {
            evictMenuCaches(menuItemId);
            publisher.publish(Topics.MENU_AVAILABILITY, new MenuInvalidated(
                    UUID.randomUUID(), Instant.now(), menuItemId, available, itemName));
        });
        return true;
    }

    /**
     * Drops the assembled menu and the item's availability entry.
     *
     * <p>Redis is shared, so this one call invalidates every menu-service instance and every
     * table session that refetches afterwards.
     */
    private void evictMenuCaches(Long menuItemId) {
        cache(MenuCacheConfig.MENU_CACHE).evict(FULL_MENU_KEY);
        cache(MenuCacheConfig.AVAILABILITY_CACHE).evict(menuItemId);
        log.debug("Evicted menu cache and availability entry for item {}", menuItemId);
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("Cache '" + name + "' is not configured");
        }
        return cache;
    }
}
