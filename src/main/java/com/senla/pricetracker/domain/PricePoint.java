package com.senla.pricetracker.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePoint(Instant fetchedAt, BigDecimal price) {
}
