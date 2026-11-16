package com.senla.pricetracker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        @NotBlank String baseUrl,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-_.]{1,20}$") String symbol,
        @NotNull @DurationMin(seconds = 1) Duration pollInterval,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration defaultRateLimitBackoff,
        @NotNull @Valid Retry retry
) {

    public record Retry(
            @Min(1) int maxAttempts,
            @NotNull Duration backoff
    ) {
    }
}
