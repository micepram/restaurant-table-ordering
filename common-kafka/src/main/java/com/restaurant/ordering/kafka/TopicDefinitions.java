package com.restaurant.ordering.kafka;

import com.restaurant.ordering.events.Topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics with explicit settings.
 *
 * <p>Defined once in the shared module so every service asks for the same shape. Topic
 * creation is idempotent, so it does not matter which service starts first — but if each
 * service declared its own partition count, whichever booted first would win and the others
 * would silently accept a topic they did not expect.
 *
 * <p>Three partitions gives the kitchen and notification consumers room to scale out while
 * keeping per-table ordering, since every event keys on table id.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
public class TopicDefinitions {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1; // single-broker local cluster

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(Topics.ORDERS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic kitchenTopic() {
        return TopicBuilder.name(Topics.KITCHEN).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic menuAvailabilityTopic() {
        return TopicBuilder.name(Topics.MENU_AVAILABILITY).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(Topics.PAYMENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic tablesTopic() {
        return TopicBuilder.name(Topics.TABLES).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
