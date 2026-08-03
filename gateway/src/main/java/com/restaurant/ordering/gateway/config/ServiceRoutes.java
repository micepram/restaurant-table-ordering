package com.restaurant.ordering.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

/**
 * Routes every public path to the service that owns it.
 *
 * <p>Defined in Java rather than YAML deliberately: the route DSL is type-checked, so a
 * renamed predicate or filter fails at compile time instead of being silently ignored at
 * runtime — which is exactly how a mistyped YAML route key behaves.
 *
 * <p>WebSocket routes are ordinary HTTP routes here. Spring Cloud Gateway detects the
 * {@code Upgrade} header and proxies the socket, so the browser only ever talks to :8080.
 */
@Configuration
public class ServiceRoutes {

    private final GatewayServiceProperties services;

    public ServiceRoutes(GatewayServiceProperties services) {
        this.services = services;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("menu", r -> r.path("/api/menu/**").uri(services.menu()))
                .route("orders", r -> r.path("/api/orders/**").uri(services.order()))
                .route("kitchen-api", r -> r.path("/api/kitchen/**").uri(services.kitchen()))
                .route("tables", r -> r.path("/api/tables/**").uri(services.table()))
                .route("payments", r -> r.path("/api/payments/**").uri(services.payment()))
                // The two WebSocket endpoints. Kept behind the gateway so all three
                // frontends have exactly one origin to configure and CORS is set once.
                .route("kitchen-ws", r -> r.path("/ws/kitchen/**").uri(services.kitchen()))
                .route("customer-ws", r -> r.path("/ws/customer/**").uri(services.notification()))
                .build();
    }

    @ConfigurationProperties(prefix = "rto.services")
    public record GatewayServiceProperties(
            String menu,
            String order,
            String kitchen,
            String table,
            String payment,
            String notification) {
    }
}
