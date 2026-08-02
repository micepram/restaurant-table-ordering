package com.restaurant.ordering.kafka;

import com.restaurant.ordering.events.DomainEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events, always keyed by {@link DomainEvent#partitionKey()}.
 *
 * <p>Routing the key through the event itself rather than leaving it to each call site is
 * what guarantees the ordering the design depends on: two events for the same table always
 * land on the same partition. A call site that forgot the key would silently round-robin
 * and let a status update overtake the order that produced it.
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> template;

    public EventPublisher(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    /**
     * Sends asynchronously and logs failures.
     *
     * <p>Callers publish after their database transaction commits, so a send failure here
     * means the local state is correct but downstream never heard about it. That is
     * recoverable by replaying from the owning service, which is why this logs rather than
     * throwing back into the caller's flow.
     */
    public void publish(String topic, DomainEvent event) {
        try {
            template.send(topic, event.partitionKey(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} to {} (key={})",
                                    event.getClass().getSimpleName(), topic, event.partitionKey(), ex);
                        } else if (log.isDebugEnabled()) {
                            log.debug("Published {} to {}-{} @ offset {}",
                                    event.getClass().getSimpleName(),
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException ex) {
            // Serialization and partitioning happen synchronously inside send(), so those
            // failures arrive here rather than on the future. Letting them escape would
            // fail a request whose database work has already committed — the caller would
            // see a 500 for a change that did happen. Log and move on, matching how an
            // async delivery failure behaves.
            log.error("Failed to publish {} to {} (key={})",
                    event.getClass().getSimpleName(), topic, event.partitionKey(), ex);
        }
    }

    /**
     * Publishes once the surrounding transaction commits, or immediately if there is none.
     *
     * <p>This is the default for anything emitted from inside a service method. Publishing
     * inline lets a consumer receive the event, call back for the row that caused it, and
     * read pre-commit state — or act on a change that then rolls back. Deferring to
     * {@code afterCommit} trades that for the opposite failure: the commit succeeds and the
     * send does not, leaving consumers behind. That one is recoverable (the owning service
     * still holds the truth and can be re-read), which is why it is the better side to err on.
     */
    public void publishAfterCommit(String topic, DomainEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(topic, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(topic, event);
            }
        });
    }
}
