package com.senla.pricetracker.binance;

import java.math.BigDecimal;

public record AvgPriceResponse(Integer mins, BigDecimal price, Long closeTime) {
}
