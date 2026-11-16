package com.senla.pricetracker.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.senla.pricetracker.domain.PriceSnapshot;

import java.time.Instant;

public record PriceResponse(
        String symbol,
        String price,
        @JsonFormat(pattern = ApiDateTimeFormat.PATTERN, timezone = ApiDateTimeFormat.TIMEZONE)
        Instant fetchedAt
) {

    public static PriceResponse from(PriceSnapshot snapshot) {
        return new PriceResponse(snapshot.getSymbol(), snapshot.getPrice().toPlainString(), snapshot.getFetchedAt());
    }
}
