package com.senla.pricetracker.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "price-trend")
public record TrendProperties(
        @NotNull @DecimalMin("0") BigDecimal flatThreshold
) {
}
