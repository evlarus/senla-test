package com.senla.pricetracker.service;

import com.senla.pricetracker.domain.PricePoint;
import com.senla.pricetracker.domain.Trend;
import com.senla.pricetracker.service.exception.InsufficientPriceDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendCalculatorTest {

    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");

    private final TrendCalculator calculator = new TrendCalculator(ONE_PERCENT);

    @Test
    void steadilyGrowingPriceIsUpward() {
        assertThat(calculator.calculate(points("100", "102", "104", "106"))).isEqualTo(Trend.UPWARD);
    }

    @Test
    void steadilyFallingPriceIsDownward() {
        assertThat(calculator.calculate(points("106", "104", "102", "100"))).isEqualTo(Trend.DOWNWARD);
    }

    @Test
    void constantPriceIsFlat() {
        assertThat(calculator.calculate(points("100", "100", "100"))).isEqualTo(Trend.FLAT);
    }

    @Test
    void changeWithinThresholdIsFlat() {
        assertThat(calculator.calculate(points("100", "100.25", "100.5"))).isEqualTo(Trend.FLAT);
    }

    @Test
    void changeExactlyAtThresholdIsFlat() {
        assertThat(calculator.calculate(points("99.5", "100.5"))).isEqualTo(Trend.FLAT);
    }

    @Test
    void singleSpikeAtTheEndDoesNotReverseFallingTrend() {
        List<PricePoint> points = points("110", "108", "106", "104", "102", "100", "98", "96", "111");

        assertThat(calculator.calculate(points)).isEqualTo(Trend.DOWNWARD);
    }

    @Test
    void unevenlySpacedPointsAreWeightedByTime() {
        List<PricePoint> points = List.of(
                new PricePoint(START, new BigDecimal("100")),
                new PricePoint(START.plusSeconds(1), new BigDecimal("100")),
                new PricePoint(START.plusSeconds(3600), new BigDecimal("110")));

        assertThat(calculator.calculate(points)).isEqualTo(Trend.UPWARD);
    }

    @Test
    void zeroThresholdTreatsAnyChangeAsTrend() {
        TrendCalculator strict = new TrendCalculator(BigDecimal.ZERO);

        assertThat(strict.calculate(points("100.00000000", "100.00000001"))).isEqualTo(Trend.UPWARD);
    }

    @Test
    void singlePointIsNotEnough() {
        assertThatThrownBy(() -> calculator.calculate(points("100")))
                .isInstanceOf(InsufficientPriceDataException.class);
    }

    @Test
    void pointsFetchedAtTheSameMomentAreNotEnough() {
        List<PricePoint> points = List.of(
                new PricePoint(START, new BigDecimal("100")),
                new PricePoint(START, new BigDecimal("101")));

        assertThatThrownBy(() -> calculator.calculate(points))
                .isInstanceOf(InsufficientPriceDataException.class);
    }

    @Test
    void negativeThresholdIsRejected() {
        assertThatThrownBy(() -> new TrendCalculator(new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<PricePoint> points(String... prices) {
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            points.add(new PricePoint(START.plusSeconds(30L * i), new BigDecimal(prices[i])));
        }
        return points;
    }
}
