package com.restaurant.ordering.payment.app;

import com.restaurant.ordering.payment.domain.Payment;
import com.restaurant.ordering.payment.domain.PaymentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records failed payment attempts so they survive the caller's rollback.
 *
 * <p>A decline makes {@code PaymentAppService.pay} throw, which rolls its transaction back —
 * and with it any attempt row written inside that transaction. The record silently
 * disappears, which is precisely the opposite of what an audit trail is for: "the card was
 * declined twice before it worked" is exactly the history a staff member needs when a
 * customer disputes a charge.
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction and commits this row in its own,
 * so it persists regardless of what happens to the payment attempt. It lives in a separate
 * bean because Spring's proxying means a self-invocation would silently ignore the
 * propagation setting and rejoin the caller's transaction.
 */
@Service
public class PaymentAuditService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuditService.class);

    private final PaymentRepository payments;

    public PaymentAuditService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long orderId,
                                    long amountCents,
                                    long tipCents,
                                    String cardLast4,
                                    Payment.Status status,
                                    String failureReason,
                                    String reference) {
        payments.save(Payment.of(orderId, amountCents, tipCents, cardLast4, status, failureReason, reference));
        log.info("Recorded {} attempt {} on order {}: {}", status, reference, orderId, failureReason);
    }
}
