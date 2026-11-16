package com.senla.pricetracker.service;

import com.senla.pricetracker.config.BinanceProperties;
import com.senla.pricetracker.domain.PricePoint;
import com.senla.pricetracker.domain.PriceSnapshot;
import com.senla.pricetracker.domain.Trend;
import com.senla.pricetracker.repository.PriceSnapshotRepository;
import com.senla.pricetracker.service.exception.PriceDataNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PriceQueryService {

    static final int PRICE_SCALE = 8;

    private static final Sort CHRONOLOGICAL = Sort.by("fetchedAt", "id").ascending();

    private final PriceSnapshotRepository repository;
    private final TrendCalculator trendCalculator;
    private final String symbol;

    public PriceQueryService(PriceSnapshotRepository repository,
                             TrendCalculator trendCalculator,
                             BinanceProperties binanceProperties) {
        this.repository = repository;
        this.trendCalculator = trendCalculator;
        this.symbol = binanceProperties.symbol();
    }

    public String getSymbol() {
        return symbol;
    }

    public PriceSnapshot getLatest() {
        return repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(symbol)
                .orElseThrow(() -> new PriceDataNotFoundException("No prices stored for " + symbol + " yet"));
    }

    public BigDecimal getAveragePrice(TimeRange range) {
        BigDecimal average = repository.findAveragePrice(symbol, range.from(), range.to());
        if (average == null) {
            throw noDataIn(range);
        }
        return average.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros();
    }

    public Page<PriceSnapshot> getPrices(TimeRange range, int page, int size) {
        return repository.findBySymbolAndFetchedAtBetween(
                symbol, range.from(), range.to(), PageRequest.of(page, size, CHRONOLOGICAL));
    }

    public Trend getTrend(TimeRange range) {
        List<PricePoint> points = repository.findPricePoints(symbol, range.from(), range.to());
        if (points.isEmpty()) {
            throw noDataIn(range);
        }
        return trendCalculator.calculate(points);
    }

    private PriceDataNotFoundException noDataIn(TimeRange range) {
        return new PriceDataNotFoundException(
                "No prices stored for %s between %s and %s".formatted(symbol, range.from(), range.to()));
    }
}
