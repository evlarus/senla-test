package com.senla.pricetracker.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senla.pricetracker.binance.exception.BinanceApiException;
import com.senla.pricetracker.binance.exception.BinanceInvalidResponseException;
import com.senla.pricetracker.binance.exception.BinanceRateLimitException;
import com.senla.pricetracker.binance.exception.BinanceRequestRejectedException;
import com.senla.pricetracker.binance.exception.BinanceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class BinanceApiClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceApiClient.class);

    private static final String AVG_PRICE_PATH = "/api/v3/avgPrice";
    private static final String SYMBOL_PARAM = "symbol";
    private static final String USED_WEIGHT_HEADER = "X-MBX-USED-WEIGHT-1M";
    private static final int IP_BANNED_STATUS = 418;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BinanceApiClient(@Qualifier("binanceRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public AvgPriceResponse fetchAveragePrice(String symbol) {
        AvgPriceResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(AVG_PRICE_PATH).queryParam(SYMBOL_PARAM, symbol).build())
                    .exchange((request, httpResponse) -> {
                        logUsedWeight(httpResponse);
                        if (httpResponse.getStatusCode().isError()) {
                            throw toApiException(httpResponse);
                        }
                        return httpResponse.bodyTo(AvgPriceResponse.class);
                    });
        } catch (ResourceAccessException e) {
            throw new BinanceUnavailableException("Binance is unreachable: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new BinanceInvalidResponseException("Cannot read Binance response: " + e.getMessage(), e);
        }
        return validate(response);
    }

    private static AvgPriceResponse validate(AvgPriceResponse response) {
        if (response == null || response.price() == null || response.closeTime() == null || response.mins() == null) {
            throw new BinanceInvalidResponseException("Binance response misses required fields: " + response);
        }
        return response;
    }

    private BinanceApiException toApiException(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.value() == IP_BANNED_STATUS) {
            Duration retryAfter = parseRetryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            return new BinanceRateLimitException(status.value(), retryAfter, body);
        }
        if (status.is5xxServerError()) {
            return new BinanceUnavailableException("Binance server error (HTTP %d): %s".formatted(status.value(), body));
        }
        BinanceError error = parseError(body);
        String details = error != null && error.msg() != null ? error.msg() : body;
        Integer code = error != null ? error.code() : null;
        return new BinanceRequestRejectedException(status.value(), code, details);
    }

    private static Duration parseRetryAfter(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(headerValue.trim()));
        } catch (NumberFormatException e) {
            log.warn("Unexpected Retry-After header value from Binance: '{}'", headerValue);
            return null;
        }
    }

    private BinanceError parseError(String body) {
        try {
            return objectMapper.readValue(body, BinanceError.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void logUsedWeight(ClientHttpResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("Binance used request weight (1m): {}", response.getHeaders().getFirst(USED_WEIGHT_HEADER));
        }
    }
}
