package com.senla.pricetracker.service;

import com.senla.pricetracker.config.BinanceProperties;
import com.senla.pricetracker.domain.PricePoint;
import com.senla.pricetracker.domain.Trend;
import com.senla.pricetracker.repository.PriceSnapshotRepository;
import com.senla.pricetracker.service.exception.PriceDataNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PriceQueryServiceTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T23:59:59Z");
    private static final TimeRange RANGE = new TimeRange(FROM, TO);

    private final PriceSnapshotRepository repository = mock(PriceSnapshotRepository.class);
    private final TrendCalculator trendCalculator = mock(TrendCalculator.class);

    private PriceQueryService service;

    @BeforeEach
    void setUp() {
        BinanceProperties properties = new BinanceProperties(
                "https://api.binance.com", SYMBOL, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofMinutes(1), new BinanceProperties.Retry(3, Duration.ofSeconds(1)));
        service = new PriceQueryService(repository, trendCalculator, properties);
    }

    @Test
    void averageIsRoundedToBinancePriceScale() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(new BigDecimal("609.2333333333333333333333"));

        assertThat(service.getAveragePrice(RANGE)).isEqualByComparingTo("609.23333333");
    }

    @Test
    void averageDropsInsignificantZeros() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(new BigDecimal("10.2000000000000000"));

        assertThat(service.getAveragePrice(RANGE).toPlainString()).isEqualTo("10.2");
    }

    @Test
    void averageWithoutDataIsNotFound() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(null);

        assertThatThrownBy(() -> service.getAveragePrice(RANGE)).isInstanceOf(PriceDataNotFoundException.class);
    }

    @Test
    void latestWithoutDataIsNotFound() {
        when(repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatest()).isInstanceOf(PriceDataNotFoundException.class);
    }

    @Test
    void trendIsCalculatedFromStoredPoints() {
        List<PricePoint> points = List.of(
                new PricePoint(FROM, new BigDecimal("100")),
                new PricePoint(FROM.plusSeconds(30), new BigDecimal("110")));
        when(repository.findPricePoints(SYMBOL, FROM, TO)).thenReturn(points);
        when(trendCalculator.calculate(points)).thenReturn(Trend.UPWARD);

        assertThat(service.getTrend(RANGE)).isEqualTo(Trend.UPWARD);
    }

    @Test
    void trendWithoutDataIsNotFound() {
        when(repository.findPricePoints(SYMBOL, FROM, TO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getTrend(RANGE)).isInstanceOf(PriceDataNotFoundException.class);
        verifyNoInteractions(trendCalculator);
    }
}
