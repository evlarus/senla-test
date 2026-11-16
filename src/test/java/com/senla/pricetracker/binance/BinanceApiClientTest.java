package com.senla.pricetracker.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.senla.pricetracker.binance.exception.BinanceInvalidResponseException;
import com.senla.pricetracker.binance.exception.BinanceRateLimitException;
import com.senla.pricetracker.binance.exception.BinanceRequestRejectedException;
import com.senla.pricetracker.binance.exception.BinanceUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceApiClientTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private static WireMockServer wireMock;
    private static BinanceApiClient client;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(requestFactory)
                .build();
        client = new BinanceApiClient(restClient, new ObjectMapper());
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void parsesAveragePrice() {
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .withQueryParam("symbol", equalTo(SYMBOL))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"mins": 5, "price": "612.34500000", "closeTime": 1788256529500}
                                """)));

        AvgPriceResponse response = client.fetchAveragePrice(SYMBOL);

        assertThat(response.mins()).isEqualTo(5);
        assertThat(response.price()).isEqualByComparingTo("612.345");
        assertThat(response.price().scale()).isEqualTo(8);
        assertThat(response.closeTime()).isEqualTo(1788256529500L);
    }

    @Test
    void tooManyRequestsCarriesRetryAfter() {
        stubError(429, "120", """
                {"code": -1003, "msg": "Too many requests."}
                """);

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL))
                .isInstanceOfSatisfying(BinanceRateLimitException.class, e -> {
                    assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(120));
                    assertThat(e.isIpBanned()).isFalse();
                });
    }

    @Test
    void teapotMeansIpBan() {
        stubError(418, "3600", """
                {"code": -1003, "msg": "Way too many requests; IP banned."}
                """);

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL))
                .isInstanceOfSatisfying(BinanceRateLimitException.class, e -> {
                    assertThat(e.getRetryAfter()).contains(Duration.ofHours(1));
                    assertThat(e.isIpBanned()).isTrue();
                });
    }

    @Test
    void rateLimitWithoutRetryAfterHasNoDuration() {
        stubError(429, null, "");

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL))
                .isInstanceOfSatisfying(BinanceRateLimitException.class,
                        e -> assertThat(e.getRetryAfter()).isEmpty());
    }

    @Test
    void invalidSymbolIsRejected() {
        stubError(400, null, """
                {"code": -1121, "msg": "Invalid symbol."}
                """);

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL))
                .isInstanceOfSatisfying(BinanceRequestRejectedException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo(-1121);
                    assertThat(e.getMessage()).contains("Invalid symbol.");
                });
    }

    @Test
    void wafViolationIsRejected() {
        stubError(403, null, "<html>Forbidden</html>");

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL))
                .isInstanceOfSatisfying(BinanceRequestRejectedException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(403));
    }

    @Test
    void serverErrorIsTransient() {
        stubError(503, null, "Service Unavailable");

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL)).isInstanceOf(BinanceUnavailableException.class);
    }

    @Test
    void connectionResetIsTransient() {
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL)).isInstanceOf(BinanceUnavailableException.class);
    }

    @Test
    void readTimeoutIsTransient() {
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .willReturn(aResponse()
                        .withFixedDelay((int) READ_TIMEOUT.multipliedBy(3).toMillis())
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"mins": 5, "price": "612.34500000", "closeTime": 1788256529500}
                                """)));

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL)).isInstanceOf(BinanceUnavailableException.class);
    }

    @Test
    void unparsableBodyIsInvalidResponse() {
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{not json")));

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL)).isInstanceOf(BinanceInvalidResponseException.class);
    }

    @Test
    void missingPriceIsInvalidResponse() {
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"mins": 5, "closeTime": 1788256529500}
                                """)));

        assertThatThrownBy(() -> client.fetchAveragePrice(SYMBOL)).isInstanceOf(BinanceInvalidResponseException.class);
    }

    private static void stubError(int status, String retryAfter, String body) {
        var response = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
        if (retryAfter != null) {
            response = response.withHeader("Retry-After", retryAfter);
        }
        wireMock.stubFor(get(urlPathEqualTo(AVG_PRICE_PATH)).willReturn(response));
    }
}
