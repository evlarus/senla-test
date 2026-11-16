package com.senla.pricetracker.web;

import com.senla.pricetracker.service.PriceQueryService;
import com.senla.pricetracker.service.TimeRange;
import com.senla.pricetracker.web.dto.AveragePriceResponse;
import com.senla.pricetracker.web.dto.PricePageResponse;
import com.senla.pricetracker.web.dto.PriceResponse;
import com.senla.pricetracker.web.dto.TrendResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_PAGE_SIZE = "20";
    static final int MAX_PAGE_SIZE = 100;

    private final PriceQueryService priceQueryService;

    public PriceController(PriceQueryService priceQueryService) {
        this.priceQueryService = priceQueryService;
    }

    @GetMapping("/latest")
    public PriceResponse getLatest() {
        return PriceResponse.from(priceQueryService.getLatest());
    }

    @GetMapping("/average")
    public AveragePriceResponse getAveragePrice(@RequestParam Instant from, @RequestParam Instant to) {
        return new AveragePriceResponse(priceQueryService.getAveragePrice(new TimeRange(from, to)));
    }

    @GetMapping
    public PricePageResponse getPrices(@RequestParam Instant from,
                                       @RequestParam Instant to,
                                       @RequestParam(defaultValue = DEFAULT_PAGE) @Min(0) int page,
                                       @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PricePageResponse.from(priceQueryService.getPrices(new TimeRange(from, to), page, size));
    }

    @GetMapping("/trend")
    public TrendResponse getTrend(@RequestParam Instant from, @RequestParam Instant to) {
        TimeRange range = new TimeRange(from, to);
        return new TrendResponse(priceQueryService.getSymbol(), from, to, priceQueryService.getTrend(range));
    }
}
