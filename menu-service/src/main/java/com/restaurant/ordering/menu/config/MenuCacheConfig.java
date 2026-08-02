package com.restaurant.ordering.menu.config;

import java.time.Duration;
import java.util.Map;

import com.restaurant.ordering.menu.api.MenuViews.AvailabilityView;
import com.restaurant.ordering.menu.api.MenuViews.MenuView;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis-backed menu cache.
 *
 * <p>Redis rather than an in-process cache is the load-bearing choice: every menu-service
 * instance reads and evicts the same keys, so a single {@code @CacheEvict} when the kitchen
 * 86s an item invalidates the menu fleet-wide. A local cache would need its own
 * invalidation broadcast to achieve the same thing.
 *
 * <p>Each cache is configured with a serializer typed to exactly what it holds. Spring Data
 * Redis 4 ships two families — {@code Jackson2*} (legacy Jackson 2) and the unprefixed
 * {@code Jackson*} (Jackson 3, {@code tools.jackson}). Boot 4 is Jackson 3, and only that
 * family handles the {@code Instant} on {@link MenuView} without extra modules. The default
 * serializer, had it been left alone, is JDK serialization, which fails on records outright.
 */
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "rto.menu")
public class MenuCacheConfig {

    /** Full assembled menu; a table session fetches it in one round trip. */
    public static final String MENU_CACHE = "menu";

    /** Per-item availability, read by order-service on every order placement. */
    public static final String AVAILABILITY_CACHE = "availability";

    private Duration cacheTtl = Duration.ofMinutes(10);

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base())
                .withInitialCacheConfigurations(Map.of(
                        MENU_CACHE, base().serializeValuesWith(json(MenuView.class)),
                        AVAILABILITY_CACHE, base().serializeValuesWith(json(AvailabilityView.class))))
                .build();
    }

    private RedisCacheConfiguration base() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // A TTL as well as explicit eviction: eviction is the fast path, and the TTL
                // is the backstop so a dropped invalidation event self-heals in minutes
                // instead of serving a stale menu for the rest of service.
                .entryTtl(cacheTtl)
                // Null results are not cached, so a miss on a deleted item re-queries rather
                // than pinning "absent" into Redis.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
    }

    private <T> RedisSerializationContext.SerializationPair<T> json(Class<T> type) {
        return RedisSerializationContext.SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<>(type));
    }
}
