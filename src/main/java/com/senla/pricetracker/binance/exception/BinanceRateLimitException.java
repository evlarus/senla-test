package com.senla.pricetracker.binance.exception;

import java.time.Duration;
import java.util.Optional;

public class BinanceRateLimitException extends BinanceApiException {

    private static final int IP_BANNED_STATUS = 418;

    private final int httpStatus;
    private final Duration retryAfter;

    public BinanceRateLimitException(int httpStatus, Duration retryAfter, String details) {
        super("Binance rate limit hit (HTTP %d): %s".formatted(httpStatus, details));
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isIpBanned() {
        return httpStatus == IP_BANNED_STATUS;
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
