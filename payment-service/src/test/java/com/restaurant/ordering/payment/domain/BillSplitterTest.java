package com.restaurant.ordering.payment.domain;

import java.util.List;

import com.restaurant.ordering.payment.domain.BillSplitter.Share;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The money arithmetic. Every test here is about the same underlying risk: a split whose
 * shares do not add back up to the bill, leaving the restaurant short or the bill unable
 * to settle.
 */
class BillSplitterTest {

    @Test
    @DisplayName("an evenly divisible bill splits into equal shares")
    void splitsEvenly() {
        assertThat(BillSplitter.splitEvenly(9000, 3)).containsExactly(3000L, 3000L, 3000L);
    }

    @Test
    @DisplayName("an indivisible bill puts the extra cents on the earliest shares")
    void distributesRemainder() {
        // 80.50 / 3 = 26.8333...; someone has to pay the extra penny.
        assertThat(BillSplitter.splitEvenly(8050, 3)).containsExactly(2684L, 2683L, 2683L);
    }

    @Test
    @DisplayName("two leftover cents land on two different shares, never both on one")
    void spreadsMultipleRemainderCents() {
        assertThat(BillSplitter.splitEvenly(8051, 3)).containsExactly(2684L, 2684L, 2683L);
    }

    /**
     * The property that actually matters: however the remainder falls, the shares must
     * reconstitute the bill exactly.
     */
    @ParameterizedTest(name = "{0} cents split {1} ways sums back to {0}")
    @CsvSource({
            "8050, 3", "8051, 3", "8052, 3", "1, 7", "0, 4", "99999, 7",
            "100, 3", "1234567, 13", "5, 6", "999, 2"
    })
    void sharesAlwaysSumToTheOriginal(long amount, int ways) {
        List<Long> shares = BillSplitter.splitEvenly(amount, ways);
        assertThat(shares).hasSize(ways);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(amount);
    }

    @ParameterizedTest(name = "no share differs from another by more than a cent ({0} / {1})")
    @CsvSource({"8050, 3", "99999, 7", "1, 7", "1234567, 13"})
    void sharesDifferByAtMostOneCent(long amount, int ways) {
        List<Long> shares = BillSplitter.splitEvenly(amount, ways);
        long min = shares.stream().mapToLong(Long::longValue).min().orElseThrow();
        long max = shares.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("splitting a bill fewer than one way is rejected")
    void rejectsNonPositiveWays() {
        assertThatThrownBy(() -> BillSplitter.splitEvenly(1000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillSplitter.splitEvenly(1000, -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> BillSplitter.splitEvenly(-1, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("18% of 80.50 is 14.49")
    void calculatesPercentageTip() {
        assertThat(BillSplitter.tipForPercent(8050, 18)).isEqualTo(1449);
    }

    @Test
    @DisplayName("a tip landing on half a cent rounds up, not down")
    void roundsTipHalfUp() {
        // 12.5% of 3.32 = 41.5 cents exactly.
        assertThat(BillSplitter.tipForPercent(332, 125 / 10)).isEqualTo(40);
        // 15% of 4.10 = 61.5 cents -> 62, not 61.
        assertThat(BillSplitter.tipForPercent(410, 15)).isEqualTo(62);
    }

    @ParameterizedTest(name = "a {0}% tip on a zero bill is zero")
    @ValueSource(ints = {0, 10, 18, 25, 100})
    void tipOnZeroBillIsZero(int percent) {
        assertThat(BillSplitter.tipForPercent(0, percent)).isZero();
    }

    @Test
    void rejectsNegativeTipPercentage() {
        assertThatThrownBy(() -> BillSplitter.tipForPercent(1000, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bill and tip are split independently so each share carries its own tip line")
    void splitsBillAndTipSeparately() {
        List<Share> shares = BillSplitter.splitWithTip(8050, 1449, 3);

        assertThat(shares).hasSize(3);
        assertThat(shares.stream().mapToLong(Share::amountCents).sum()).isEqualTo(8050);
        assertThat(shares.stream().mapToLong(Share::tipCents).sum()).isEqualTo(1449);
        assertThat(shares.stream().mapToLong(Share::totalCents).sum()).isEqualTo(9499);
        assertThat(shares).extracting(Share::position).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("splitting the sum instead would hand one payer both remainders")
    void separateSplitsAvoidStackingRemainders() {
        // 8050 leaves 1 cent over 3 ways, and 1449 leaves 0 -- but 8051/1450 leaves one of
        // each. Splitting separately puts them on the same payer only by coincidence, never
        // by construction, and each payer's tip stays displayable.
        List<Share> shares = BillSplitter.splitWithTip(8051, 1450, 3);

        assertThat(shares.stream().mapToLong(Share::amountCents).sum()).isEqualTo(8051);
        assertThat(shares.stream().mapToLong(Share::tipCents).sum()).isEqualTo(1450);
        // Combined split of 9501 would be 3167/3167/3167 = 9501 with no way to say how much
        // of each share was tip.
        assertThat(shares.get(0).tipCents() + shares.get(1).tipCents() + shares.get(2).tipCents())
                .isEqualTo(1450);
    }

    @Test
    @DisplayName("a bill paid by one person is a one-way split")
    void singleWaySplitReturnsWholeBill() {
        assertThat(BillSplitter.splitEvenly(8050, 1)).containsExactly(8050L);
    }
}
