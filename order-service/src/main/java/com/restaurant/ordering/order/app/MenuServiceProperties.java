package com.restaurant.ordering.order.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rto.menu-service")
public record MenuServiceProperties(String baseUrl) {

    public MenuServiceProperties {
        baseUrl = baseUrl == null ? "http://localhost:8081" : baseUrl;
    }
}
