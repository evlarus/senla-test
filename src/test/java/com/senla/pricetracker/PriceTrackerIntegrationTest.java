package com.senla.pricetracker;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.senla.pricetracker.repository.PriceSnapshotRepository;
import com.senla.pricetracker.service.PollOutcome;
import com.senla.pricetracker.service.PricePollingService;
import com.senla.pricetracker.support.MutableClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "price-polling.enabled=false",
        "binance.symbol=BNBUSDT",
        "binance.retry.max-attempts=3",
        "binance.retry.backoff=PT0.01S"
})
@AutoConfigureMockMvc
class PriceTrackerIntegrationTest {

    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";
    private static final String SYMBOL = "BNBUSDT";
    private static final Duration POLL_STEP = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final WireMockServer BINANCE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        BINANCE.start();
    }

    @DynamicPropertySource
    static void binanceProperties(DynamicPropertyRegistry registry) {
        registry.add("binance.base-url", BINANCE::baseUrl);
    }

    @AfterAll
    static void stopBinance() {
        BINANCE.stop();
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PricePollingService pricePollingService;

    @Autowired
    private PriceSnapshotRepository repository;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        BINANCE.resetAll();
        clock.advance(Duration.ofDays(1));
    }

    @Test
    void pollsBinanceStoresPricesAndServesThemOverRest() throws Exception {
        Instant start = clock.instant();

        stubPrice("600.00000000", start.minusMillis(500));
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.SAVED);

        clock.advance(POLL_STEP);
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.DUPLICATE);

        clock.advance(POLL_STEP);
        stubPrice("612.50000000", clock.instant().minusMillis(500));
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.SAVED);

        clock.advance(POLL_STEP);
        BINANCE.resetAll();
        BINANCE.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH)).willReturn(aResponse().withStatus(503)));
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.FAILED);
        BINANCE.verify(3, getRequestedFor(urlPathEqualTo(AVG_PRICE_PATH)));

        clock.advance(POLL_STEP);
        Instant lastFetch = clock.instant();
        stubPrice("615.10000000", lastFetch.minusMillis(500));
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.SAVED);

        assertThat(repository.count()).isEqualTo(3);

        mockMvc.perform(apiGet("/api/v1/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(SYMBOL))
                .andExpect(jsonPath("$.price").value("615.10000000"))
                .andExpect(jsonPath("$.fetchedAt").value(lastFetch.toString()));

        mockMvc.perform(apiGet("/api/v1/prices/average", start, lastFetch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice").value(609.2));

        mockMvc.perform(apiGet("/api/v1/prices/average", start, start))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice").value(600));

        mockMvc.perform(apiGet("/api/v1/prices", start, lastFetch).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].price").value("600.00000000"))
                .andExpect(jsonPath("$.items[1].price").value("612.50000000"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(apiGet("/api/v1/prices", start, lastFetch).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].price").value("615.10000000"))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(apiGet("/api/v1/prices/trend", start, lastFetch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(SYMBOL))
                .andExpect(jsonPath("$.start_date").value(start.toString()))
                .andExpect(jsonPath("$.end_date").value(lastFetch.toString()))
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }

    @Test
    void rateLimitStopsCallsToBinanceUntilRetryAfterPasses() {
        Duration retryAfter = Duration.ofSeconds(120);
        BINANCE.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", String.valueOf(retryAfter.toSeconds()))
                        .withBody("""
                                {"code": -1003, "msg": "Too many requests."}
                                """)));

        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.RATE_LIMITED);

        clock.advance(POLL_STEP);
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.RATE_LIMITED);
        BINANCE.verify(1, getRequestedFor(urlPathEqualTo(AVG_PRICE_PATH)));

        clock.advance(retryAfter);
        stubPrice("600.00000000", clock.instant().minusMillis(500));
        assertThat(pricePollingService.poll()).isEqualTo(PollOutcome.SAVED);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void emptyIntervalIsNotFound() throws Exception {
        Instant from = clock.instant();

        mockMvc.perform(apiGet("/api/v1/prices/average", from, from.plusSeconds(60)))
                .andExpect(status().isNotFound());

        mockMvc.perform(apiGet("/api/v1/prices", from, from.plusSeconds(60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    private static void stubPrice(String price, Instant closeTime) {
        BINANCE.resetAll();
        BINANCE.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .withQueryParam("symbol", equalTo(SYMBOL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"mins": 5, "price": "%s", "closeTime": %d}
                                """.formatted(price, closeTime.toEpochMilli()))));
    }

    private static MockHttpServletRequestBuilder apiGet(String path) {
        return MockMvcRequestBuilders.get(path);
    }

    private static MockHttpServletRequestBuilder apiGet(String path, Instant from, Instant to) {
        return apiGet(path).param("from", from.toString()).param("to", to.toString());
    }
}
