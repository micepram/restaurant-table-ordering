package com.restaurant.ordering.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Registers the shared {@link EventPublisher}.
 *
 * <p>Everything else about the Kafka setup is left to Spring Boot's own auto-configuration,
 * driven by {@code classpath:rto-kafka-defaults.yml}. Building the producer and consumer
 * factories here instead would mean racing Boot's {@code @ConditionalOnMissingBean} and
 * pinning this module to the internal layout of Boot's Kafka auto-configuration package.
 */
/*
 * Ordered explicitly after Boot's KafkaAutoConfiguration. Auto-configurations are
 * evaluated in a defined order, and an unordered one that asks about beans another
 * auto-configuration has not registered yet gets the wrong answer — here that surfaced
 * as a missing EventPublisher at injection time, with no hint that ordering was the cause.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
public class RtoKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(KafkaTemplate<String, Object> template) {
        return new EventPublisher(template);
    }
}
