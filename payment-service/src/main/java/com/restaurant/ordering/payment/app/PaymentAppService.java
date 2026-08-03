package com.restaurant.ordering.payment.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.restaurant.ordering.events.PaymentCompleted;
import com.restaurant.ordering.events.Topics;
import com.restaurant.ordering.kafka.EventPublisher;
import com.restaurant.ordering.payment.api.PaymentDtos.BillView;
import com.restaurant.ordering.payment.api.PaymentDtos.SplitView;
import com.restaurant.ordering.payment.domain.Bill;
import com.restaurant.ordering.payment.domain.BillRepository;
import com.restaurant.ordering.payment.domain.BillSplitter;
import com.restaurant.ordering.payment.domain.Payment;
import com.restaurant.ordering.payment.domain.PaymentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAppService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAppService.class);

    private final BillRepository bills;
    private final PaymentRepository payments;
    private final OrderClient orderClient;
    private final MockCardProcessor processor;
    private final PaymentAuditService audit;
    private final EventPublisher publisher;

    public PaymentAppService(BillRepository bills,
                             PaymentRepository payments,
                             OrderClient orderClient,
                             MockCardProcessor processor,
                             PaymentAuditService audit,
                             EventPublisher publisher) {
        this.bills = bills;
        this.payments = payments;
        this.orderClient = orderClient;
        this.processor = processor;
        this.audit = audit;
        this.publisher = publisher;
    }

    /**
     * Opens the bill for an order, or returns the existing one.
     *
     * <p>The subtotal is snapshotted here and never re-read. If it were refreshed on each
     * payment, a split that is halfway settled could have its total change underneath the
     * remaining payers.
     */
    @Transactional
    public BillView openBill(Long orderId, String bearerToken) {
        Bill bill = bills.findById(orderId).orElseGet(() -> {
            OrderClient.OrderSnapshot order = orderClient.fetchOrder(orderId, bearerToken);
            log.info("Opening bill for order {} (table {}, subtotal {})",
                    orderId, order.tableCode(), order.subtotalCents());
            return bills.save(Bill.open(orderId, order.tableId(), order.tableCode(), order.subtotalCents()));
        });
        return view(bill);
    }

    @Transactional(readOnly = true)
    public BillView getBill(Long orderId) {
        return view(require(orderId));
    }

    @Transactional
    public BillView setTip(Long orderId, Integer percent, Long amountCents) {
        Bill bill = require(orderId);
        long tip = percent != null
                ? BillSplitter.tipForPercent(bill.getSubtotalCents(), percent)
                : (amountCents == null ? 0 : amountCents);
        bill.setTip(tip);
        log.info("Bill {} tip set to {} cents", orderId, tip);
        return view(bill);
    }

    /**
     * Previews an even split. A calculation only — nothing is charged and nothing is stored.
     *
     * <p>The bill and the tip are split separately so each payer's receipt can show its own
     * tip line, and so one payer does not absorb both remainders.
     */
    @Transactional(readOnly = true)
    public SplitView splitEvenly(Long orderId, int ways) {
        Bill bill = require(orderId);
        List<BillSplitter.Share> shares =
                BillSplitter.splitWithTip(bill.getSubtotalCents(), bill.getTipCents(), ways);
        return new SplitView(ways, bill.getSubtotalCents(), bill.getTipCents(), bill.totalCents(), shares);
    }

    /**
     * Charges a card against the bill.
     *
     * <p>A declined attempt is still written to the payment table — the history of failed
     * attempts is what a staff member needs when a customer disputes a charge — but the bill
     * itself is untouched, so nothing is owed differently as a result.
     *
     * <p>{@link PaymentCompleted} carries {@code settledInFull}, and order-service moves the
     * order to PAID only on that flag. With a split bill, the first of three payers must not
     * close the order.
     *
     * @throws PaymentDeclinedException if the processor refuses the card
     */
    @Transactional
    public BillView pay(Long orderId, long amountCents, long tipCents, String cardNumber) {
        Bill bill = require(orderId);
        MockCardProcessor.Result result = processor.authorise(cardNumber, amountCents + tipCents);

        if (!result.approved()) {
            // Written in its own transaction: throwing below rolls this one back, and a
            // decline recorded inside it would vanish with it.
            audit.recordFailedAttempt(orderId, amountCents, tipCents, result.cardLast4(),
                    result.status(), result.failureReason(), result.reference());
            throw new PaymentDeclinedException(result.failureReason(), result.reference());
        }

        // Charged amount includes the tip portion, so the outstanding balance covers both.
        bill.applyPayment(amountCents + tipCents);
        Payment saved = payments.save(Payment.of(orderId, amountCents, tipCents, result.cardLast4(),
                Payment.Status.APPROVED, null, result.reference()));

        log.info("Payment {} approved on bill {}: {} + {} tip, {} outstanding",
                result.reference(), orderId, amountCents, tipCents, bill.getOutstandingCents());

        publisher.publishAfterCommit(Topics.PAYMENTS, new PaymentCompleted(
                UUID.randomUUID(),
                Instant.now(),
                saved.getId(),
                orderId,
                bill.getTableId(),
                amountCents,
                tipCents,
                bill.getOutstandingCents(),
                bill.isSettled()));

        return view(bill);
    }

    private Bill require(Long orderId) {
        return bills.findById(orderId)
                .orElseThrow(() -> new BillNotFoundException("No bill for order " + orderId));
    }

    private BillView view(Bill bill) {
        return BillView.of(bill, payments.findByOrderIdOrderByCreatedAtAsc(bill.getOrderId()));
    }
}
