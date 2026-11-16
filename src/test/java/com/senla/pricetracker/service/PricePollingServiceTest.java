package com.senla.pricetracker.service;

import com.senla.pricetracker.binance.AvgPriceResponse;
import com.senla.pricetracker.binance.BinanceApiClient;
import com.senla.pricetracker.binance.exception.BinanceInvalidResponseException;
import com.senla.pricetracker.binance.exception.BinanceRateLimitException;
import com.senla.pricetracker.binance.exception.BinanceRequestRejectedException;
import com.senla.pricetracker.binance.exception.BinanceUnavailableException;
import com.senla.pricetracker.config.BinanceProperties;
import com.senla.pricetracker.repository.PriceSnapshotRepository;
import com.senla.pricetracker.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PricePollingServiceTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);
    private static final Duration DEFAULT_RATE_LIMIT_BACKOFF = Duration.ofMinutes(1);
    private static final Instant NOW = Instant.parse("2026-09-01T10:15:30.789Z");
    private static final AvgPriceResponse RESPONSE =
            new AvgPriceResponse(5, new BigDecimal("612.34500000"), Instant.parse("2026-09-01T10:15:29.500Z").toEpochMilli());

    private final BinanceApiClient client = mock(BinanceApiClient.class);
    private final PriceSnapshotRepository repository = mock(PriceSnapshotRepository.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final List<Duration> sleeps = new ArrayList<>();

    private PricePollingService service;

    @BeforeEach
    void setUp() {
        BinanceProperties properties = new BinanceProperties(
                "https://api.binance.com", SYMBOL, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(10),
                DEFAULT_RATE_LIMIT_BACKOFF, new BinanceProperties.Retry(MAX_ATTEMPTS, RETRY_BACKOFF));
        service = new PricePollingService(
                client, repository, new BinanceRateLimitGuard(clock), properties, clock, sleeps::add);
    }

    @Test
    void storesFetchedPriceWithFetchTimeTruncatedToSeconds() {
        when(client.fetchAveragePrice(SYMBOL)).thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any())).thenReturn(1);

        assertThat(service.poll()).isEqualTo(PollOutcome.SAVED);

        verify(repository).insertIfAbsent(SYMBOL, RESPONSE.price(), Instant.ofEpochMilli(RESPONSE.closeTime()),
                RESPONSE.mins(), Instant.parse("2026-09-01T10:15:30Z"));
    }

    @Test
    void reportsDuplicateWhenNothingWasInserted() {
        when(client.fetchAveragePrice(SYMBOL)).thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any())).thenReturn(0);

        assertThat(service.poll()).isEqualTo(PollOutcome.DUPLICATE);
    }

    @Test
    void retriesTransientFailureWithGrowingPause() {
        when(client.fetchAveragePrice(SYMBOL))
                .thenThrow(new BinanceUnavailableException("503"))
                .thenThrow(new BinanceUnavailableException("timeout"))
                .thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any())).thenReturn(1);

        assertThat(service.poll()).isEqualTo(PollOutcome.SAVED);

        verify(client, times(3)).fetchAveragePrice(SYMBOL);
        assertThat(sleeps).containsExactly(RETRY_BACKOFF, RETRY_BACKOFF.multipliedBy(2));
    }

    @Test
    void givesUpAfterMaxAttemptsWithoutThrowing() {
        when(client.fetchAveragePrice(SYMBOL)).thenThrow(new BinanceUnavailableException("503"));

        assertThat(service.poll()).isEqualTo(PollOutcome.FAILED);

        verify(client, times(MAX_ATTEMPTS)).fetchAveragePrice(SYMBOL);
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotRetryRejectedRequest() {
        when(client.fetchAveragePrice(SYMBOL))
                .thenThrow(new BinanceRequestRejectedException(400, -1121, "Invalid symbol."));

        assertThat(service.poll()).isEqualTo(PollOutcome.FAILED);

        verify(client, times(1)).fetchAveragePrice(SYMBOL);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void doesNotRetryInvalidResponse() {
        when(client.fetchAveragePrice(SYMBOL)).thenThrow(new BinanceInvalidResponseException("no price"));

        assertThat(service.poll()).isEqualTo(PollOutcome.FAILED);

        verify(client, times(1)).fetchAveragePrice(SYMBOL);
    }

    @Test
    void rateLimitPausesPollingForRetryAfterPeriod() {
        Duration retryAfter = Duration.ofSeconds(120);
        when(client.fetchAveragePrice(SYMBOL))
                .thenThrow(new BinanceRateLimitException(429, retryAfter, "Too many requests"))
                .thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any())).thenReturn(1);

        assertThat(service.poll()).isEqualTo(PollOutcome.RATE_LIMITED);

        clock.advance(retryAfter.minusSeconds(1));
        assertThat(service.poll()).isEqualTo(PollOutcome.RATE_LIMITED);
        verify(client, times(1)).fetchAveragePrice(SYMBOL);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.poll()).isEqualTo(PollOutcome.SAVED);
        verify(client, times(2)).fetchAveragePrice(SYMBOL);
    }

    @Test
    void rateLimitWithoutRetryAfterUsesDefaultBackoff() {
        when(client.fetchAveragePrice(SYMBOL))
                .thenThrow(new BinanceRateLimitException(418, null, "IP banned"));

        service.poll();

        clock.advance(DEFAULT_RATE_LIMIT_BACKOFF.minusSeconds(1));
        assertThat(service.poll()).isEqualTo(PollOutcome.RATE_LIMITED);
        verify(client, times(1)).fetchAveragePrice(SYMBOL);
    }

    @Test
    void databaseFailureDoesNotEscape() {
        when(client.fetchAveragePrice(SYMBOL)).thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThat(service.poll()).isEqualTo(PollOutcome.FAILED);
    }

    @Test
    void failedPollDoesNotPreventNextOne() {
        when(client.fetchAveragePrice(SYMBOL))
                .thenThrow(new BinanceRequestRejectedException(403, null, "WAF"))
                .thenReturn(RESPONSE);
        when(repository.insertIfAbsent(anyString(), any(), any(), anyInt(), any())).thenReturn(1);

        assertThat(service.poll()).isEqualTo(PollOutcome.FAILED);
        assertThat(service.poll()).isEqualTo(PollOutcome.SAVED);
        verify(repository, times(1)).insertIfAbsent(anyString(), any(), any(), anyInt(), any());
    }
}
