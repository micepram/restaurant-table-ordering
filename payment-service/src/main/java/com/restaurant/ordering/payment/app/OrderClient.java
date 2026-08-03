package com.restaurant.ordering.payment.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads an order's total from order-service when a bill is first opened. */
@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final RestClient restClient;

    public OrderClient(RestClient.Builder builder,
                       @Value("${rto.order-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public OrderSnapshot fetchOrder(Long orderId, String bearerToken) {
        try {
            OrderSnapshot order = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(OrderSnapshot.class);
            if (order == null) {
                throw new OrderUnavailableException("order-service returned no body for order " + orderId);
            }
            return order;
        } catch (RestClientException ex) {
            log.warn("order-service lookup failed for order {}", orderId, ex);
            throw new OrderUnavailableException("Could not reach order-service for order " + orderId, ex);
        }
    }

    /** Subset of order-service's OrderView that payment needs. */
    public record OrderSnapshot(Long id, Long tableId, String tableCode, String status, long subtotalCents) {
    }
}
