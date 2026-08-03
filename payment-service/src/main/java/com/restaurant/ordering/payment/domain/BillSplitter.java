package com.restaurant.ordering.payment.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Money arithmetic for splitting bills and calculating tips.
 *
 * <p>Everything is integer cents. A bill split three ways is the operation this system does
 * most often and is exactly where a decimal or floating-point representation loses money:
 * £80.50 / 3 has no exact representation, and rounding each share independently produces a
 * set of shares that does not add back up to the bill. Working in cents makes the remainder
 * an explicit value that has to be assigned to someone.
 */
public final class BillSplitter {

    private BillSplitter() {
    }

    /**
     * Splits an amount into {@code ways} shares that sum <em>exactly</em> to the original.
     *
     * <p>The indivisible remainder is spread one cent at a time over the earliest shares, so
     * a £80.50 bill split three ways becomes 26.84 / 26.83 / 26.83 rather than three shares
     * of 26.83 that leave a penny unpaid. Some payers are charged a cent more than others;
     * that is unavoidable, and the alternative — a bill that never settles — is worse.
     *
     * @throws IllegalArgumentException if {@code ways} is not positive or the amount is negative
     */
    public static List<Long> splitEvenly(long amountCents, int ways) {
        if (ways <= 0) {
            throw new IllegalArgumentException("Cannot split a bill " + ways + " ways");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("Cannot split a negative amount");
        }

        long base = amountCents / ways;
        // Always in [0, ways), so at most one extra cent lands on any single share.
        int remainder = (int) (amountCents - base * ways);

        List<Long> shares = new ArrayList<>(ways);
        for (int i = 0; i < ways; i++) {
            shares.add(i < remainder ? base + 1 : base);
        }
        return shares;
    }

    /**
     * Tip as a percentage of an amount, rounded half-up.
     *
     * <p>Integer maths rather than {@code amount * percent / 100.0}: a double would make
     * 18% of some totals land a cent low through binary representation error, and a tip
     * that disagrees with what the customer was shown is a support ticket.
     *
     * @param percent whole percentage points, e.g. 18 for 18%
     */
    public static long tipForPercent(long amountCents, int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("Tip percentage cannot be negative");
        }
        // + 50 before the divide is the half-up rounding step.
        return (amountCents * percent + 50) / 100;
    }

    /**
     * Splits a bill and its tip together, keeping both exact.
     *
     * <p>The tip is split separately rather than added to the total and split once. Splitting
     * the sum can hand one payer both remainders — a cent of bill and a cent of tip — and
     * makes the per-share tip impossible to show on a receipt.
     */
    public static List<Share> splitWithTip(long subtotalCents, long tipCents, int ways) {
        List<Long> amounts = splitEvenly(subtotalCents, ways);
        List<Long> tips = splitEvenly(tipCents, ways);

        List<Share> shares = new ArrayList<>(ways);
        for (int i = 0; i < ways; i++) {
            shares.add(new Share(i + 1, amounts.get(i), tips.get(i)));
        }
        return shares;
    }

    /**
     * One payer's portion.
     *
     * @param position 1-based, so a receipt can say "share 2 of 3"
     */
    public record Share(int position, long amountCents, long tipCents) {

        public long totalCents() {
            return amountCents + tipCents;
        }
    }
}
