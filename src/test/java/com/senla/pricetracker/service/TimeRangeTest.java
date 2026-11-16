package com.senla.pricetracker.service;

import com.senla.pricetracker.service.exception.InvalidTimeRangeException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRangeTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void acceptsEqualBounds() {
        assertThat(new TimeRange(FROM, FROM).to()).isEqualTo(FROM);
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> new TimeRange(FROM.plusSeconds(1), FROM))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void rejectsMissingBounds() {
        assertThatThrownBy(() -> new TimeRange(null, FROM)).isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> new TimeRange(FROM, null)).isInstanceOf(InvalidTimeRangeException.class);
    }
}
