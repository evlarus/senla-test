package com.senla.pricetracker.service;

import com.senla.pricetracker.domain.PricePoint;
import com.senla.pricetracker.domain.Trend;
import com.senla.pricetracker.service.exception.InsufficientPriceDataException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class TrendCalculator {

    private static final int MIN_POINTS = 2;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    private final BigDecimal flatThreshold;

    public TrendCalculator(BigDecimal flatThreshold) {
        if (flatThreshold == null || flatThreshold.signum() < 0) {
            throw new IllegalArgumentException("flatThreshold must be a non-negative number");
        }
        this.flatThreshold = flatThreshold;
    }

    public Trend calculate(List<PricePoint> points) {
        if (points.size() < MIN_POINTS) {
            throw new InsufficientPriceDataException(
                    "At least %d prices are required to calculate a trend, found %d".formatted(MIN_POINTS, points.size()));
        }

        Instant origin = points.getFirst().fetchedAt();
        int count = points.size();
        BigDecimal[] seconds = new BigDecimal[count];
        BigDecimal[] prices = new BigDecimal[count];
        BigDecimal sumSeconds = BigDecimal.ZERO;
        BigDecimal sumPrices = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            PricePoint point = points.get(i);
            seconds[i] = BigDecimal.valueOf(Duration.between(origin, point.fetchedAt()).toMillis())
                    .movePointLeft(3);
            prices[i] = point.price();
            sumSeconds = sumSeconds.add(seconds[i]);
            sumPrices = sumPrices.add(prices[i]);
        }

        BigDecimal size = BigDecimal.valueOf(count);
        BigDecimal meanSeconds = sumSeconds.divide(size, MATH_CONTEXT);
        BigDecimal meanPrice = sumPrices.divide(size, MATH_CONTEXT);

        BigDecimal covariance = BigDecimal.ZERO;
        BigDecimal timeVariance = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal timeDeviation = seconds[i].subtract(meanSeconds);
            covariance = covariance.add(timeDeviation.multiply(prices[i].subtract(meanPrice)));
            timeVariance = timeVariance.add(timeDeviation.multiply(timeDeviation));
        }

        if (timeVariance.signum() == 0) {
            throw new InsufficientPriceDataException("All prices were fetched at the same moment, trend is undefined");
        }
        if (meanPrice.signum() == 0) {
            return Trend.FLAT;
        }

        BigDecimal slopePerSecond = covariance.divide(timeVariance, MATH_CONTEXT);
        BigDecimal span = seconds[count - 1].subtract(seconds[0]);
        BigDecimal relativeChange = slopePerSecond.multiply(span).divide(meanPrice, MATH_CONTEXT);

        if (relativeChange.abs().compareTo(flatThreshold) <= 0) {
            return Trend.FLAT;
        }
        return relativeChange.signum() > 0 ? Trend.UPWARD : Trend.DOWNWARD;
    }
}
