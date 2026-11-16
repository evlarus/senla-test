package com.senla.pricetracker.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senla.pricetracker.domain.Trend;

import java.time.Instant;

public record TrendResponse(
        String symbol,
        @JsonProperty("start_date")
        @JsonFormat(pattern = ApiDateTimeFormat.PATTERN, timezone = ApiDateTimeFormat.TIMEZONE)
        Instant startDate,
        @JsonProperty("end_date")
        @JsonFormat(pattern = ApiDateTimeFormat.PATTERN, timezone = ApiDateTimeFormat.TIMEZONE)
        Instant endDate,
        Trend trend
) {
}
