package com.senla.pricetracker.service;

import com.senla.pricetracker.binance.AvgPriceResponse;
import com.senla.pricetracker.binance.BinanceApiClient;
import com.senla.pricetracker.binance.exception.BinanceApiException;
import com.senla.pricetracker.binance.exception.BinanceRateLimitException;
import com.senla.pricetracker.binance.exception.BinanceUnavailableException;
import com.senla.pricetracker.config.BinanceProperties;
import com.senla.pricetracker.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PricePollingService {

    private static final Logger log = LoggerFactory.getLogger(PricePollingService.class);

    private final BinanceApiClient binanceApiClient;
    private final PriceSnapshotRepository repository;
    private final BinanceRateLimitGuard rateLimitGuard;
    private final BinanceProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;

    public PricePollingService(BinanceApiClient binanceApiClient,
                               PriceSnapshotRepository repository,
                               BinanceRateLimitGuard rateLimitGuard,
                               BinanceProperties properties,
                               Clock clock,
                               Sleeper sleeper) {
        this.binanceApiClient = binanceApiClient;
        this.repository = repository;
        this.rateLimitGuard = rateLimitGuard;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public PollOutcome poll() {
        String symbol = properties.symbol();
        if (rateLimitGuard.isBlocked()) {
            log.warn("Skipping {} poll: Binance rate limit back-off is active until {}",
                    symbol, rateLimitGuard.getBlockedUntil());
            return PollOutcome.RATE_LIMITED;
        }

        try {
            AvgPriceResponse response = fetchWithRetry(symbol);
            Instant fetchedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            return store(symbol, response, fetchedAt);
        } catch (BinanceRateLimitException e) {
            Duration backoff = e.getRetryAfter().orElse(properties.defaultRateLimitBackoff());
            rateLimitGuard.blockFor(backoff);
            log.error("{} -> Binance polling paused for {}{}", e.getMessage(), backoff,
                    e.isIpBanned() ? " (IP is banned)" : "");
            return PollOutcome.RATE_LIMITED;
        } catch (BinanceApiException e) {
            log.error("Failed to fetch {} average price from Binance: {}", symbol, e.getMessage());
            return PollOutcome.FAILED;
        } catch (DataAccessException e) {
            log.error("Failed to store {} average price", symbol, e);
            return PollOutcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} poll interrupted", symbol);
            return PollOutcome.FAILED;
        }
    }

    private AvgPriceResponse fetchWithRetry(String symbol) throws InterruptedException {
        BinanceProperties.Retry retry = properties.retry();
        for (int attempt = 1; ; attempt++) {
            try {
                return binanceApiClient.fetchAveragePrice(symbol);
            } catch (BinanceUnavailableException e) {
                if (attempt >= retry.maxAttempts()) {
                    throw e;
                }
                Duration pause = retry.backoff().multipliedBy(attempt);
                log.warn("Attempt {}/{} to fetch {} failed: {}. Retrying in {}",
                        attempt, retry.maxAttempts(), symbol, e.getMessage(), pause);
                sleeper.sleep(pause);
            }
        }
    }

    private PollOutcome store(String symbol, AvgPriceResponse response, Instant fetchedAt) {
        Instant closeTime = Instant.ofEpochMilli(response.closeTime());
        int inserted = repository.insertIfAbsent(symbol, response.price(), closeTime, response.mins(), fetchedAt);
        if (inserted == 0) {
            log.info("{} price {} (close time {}) is already stored, skipping", symbol, response.price(), closeTime);
            return PollOutcome.DUPLICATE;
        }
        log.info("Stored {} average price {} fetched at {}", symbol, response.price(), fetchedAt);
        return PollOutcome.SAVED;
    }
}
