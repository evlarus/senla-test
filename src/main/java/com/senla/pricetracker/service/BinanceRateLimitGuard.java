package com.senla.pricetracker.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class BinanceRateLimitGuard {

    private final Clock clock;
    private volatile Instant blockedUntil = Instant.EPOCH;

    public BinanceRateLimitGuard(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked() {
        return clock.instant().isBefore(blockedUntil);
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public void blockFor(Duration duration) {
        blockedUntil = clock.instant().plus(duration);
    }
}
