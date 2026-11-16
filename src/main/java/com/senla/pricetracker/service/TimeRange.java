package com.senla.pricetracker.service;

import com.senla.pricetracker.service.exception.InvalidTimeRangeException;

import java.time.Instant;

public record TimeRange(Instant from, Instant to) {

    public TimeRange {
        if (from == null || to == null) {
            throw new InvalidTimeRangeException("Both 'from' and 'to' must be provided");
        }
        if (from.isAfter(to)) {
            throw new InvalidTimeRangeException("'from' (%s) must not be after 'to' (%s)".formatted(from, to));
        }
    }
}
