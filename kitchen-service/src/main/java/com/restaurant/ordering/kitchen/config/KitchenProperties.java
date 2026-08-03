package com.restaurant.ordering.kitchen.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param wsGroup    per-instance Kafka group: every instance sees every event so it can
 *                   both project it and push to the browsers it holds
 * @param warnAfter  ticket age at which the board turns amber
 * @param lateAfter  ticket age at which the board turns red
 */
@ConfigurationProperties(prefix = "rto.kitchen")
public record KitchenProperties(
        String wsGroup,
        Duration warnAfter,
        Duration lateAfter) {

    public KitchenProperties {
        wsGroup = wsGroup == null ? "kitchen-ws" : wsGroup;
        warnAfter = warnAfter == null ? Duration.ofMinutes(5) : warnAfter;
        lateAfter = lateAfter == null ? Duration.ofMinutes(10) : lateAfter;
    }
}
