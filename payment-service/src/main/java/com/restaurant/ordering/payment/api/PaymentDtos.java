package com.restaurant.ordering.payment.api;

import java.time.Instant;
import java.util.List;

import com.restaurant.ordering.payment.domain.Bill;
import com.restaurant.ordering.payment.domain.BillSplitter.Share;
import com.restaurant.ordering.payment.domain.Payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record BillView(
            Long orderId,
            Long tableId,
            String tableCode,
            long subtotalCents,
            long tipCents,
            long totalCents,
            long outstandingCents,
            boolean settled,
            Instant updatedAt,
            List<PaymentView> payments) {

        public static BillView of(Bill bill, List<Payment> payments) {
            return new BillView(
                    bill.getOrderId(),
                    bill.getTableId(),
                    bill.getTableCode(),
                    bill.getSubtotalCents(),
                    bill.getTipCents(),
                    bill.totalCents(),
                    bill.getOutstandingCents(),
                    bill.isSettled(),
                    bill.getUpdatedAt(),
                    payments.stream().map(PaymentView::of).toList());
        }
    }

    public record PaymentView(
            Long id,
            long amountCents,
            long tipCents,
            String cardLast4,
            Payment.Status status,
            String failureReason,
            String reference,
            Instant createdAt) {

        public static PaymentView of(Payment payment) {
            return new PaymentView(
                    payment.getId(),
                    payment.getAmountCents(),
                    payment.getTipCents(),
                    payment.getCardLast4(),
                    payment.getStatus(),
                    payment.getFailureReason(),
                    payment.getReference(),
                    payment.getCreatedAt());
        }
    }

    /**
     * Tip for the whole bill.
     *
     * <p>Either a percentage or an explicit amount; percentage wins if both are sent, since
     * that is what the customer tapped.
     */
    public record TipRequest(
            @Min(0) @Max(100) Integer percent,
            @Min(0) Long amountCents) {
    }

    /** Preview of an even split. Purely a calculation — nothing is charged. */
    public record SplitView(int ways, long subtotalCents, long tipCents, long totalCents, List<Share> shares) {
    }

    /**
     * A payment attempt.
     *
     * <p>The card number never leaves this request: only the last four digits are persisted.
     * Use a number ending 0000 to force a decline or 0001 to force a processor failure.
     */
    public record PayRequest(
            @Min(1) long amountCents,
            @Min(0) long tipCents,
            @NotBlank @Pattern(regexp = "[0-9 -]{12,25}", message = "Card number must be 12-25 digits")
            String cardNumber) {
    }
}
