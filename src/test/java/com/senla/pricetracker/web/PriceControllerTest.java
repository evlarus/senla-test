package com.senla.pricetracker.web;

import com.senla.pricetracker.domain.PriceSnapshot;
import com.senla.pricetracker.domain.Trend;
import com.senla.pricetracker.service.PriceQueryService;
import com.senla.pricetracker.service.TimeRange;
import com.senla.pricetracker.service.exception.InsufficientPriceDataException;
import com.senla.pricetracker.service.exception.PriceDataNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
class PriceControllerTest {

    private static final String FROM = "2026-09-01T00:00:00Z";
    private static final String TO = "2026-09-30T23:59:59Z";
    private static final Instant FETCHED_AT = Instant.parse("2026-09-15T12:30:45Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceQueryService priceQueryService;

    @Test
    void latestPriceHasSymbolPriceAndFetchTime() throws Exception {
        when(priceQueryService.getLatest()).thenReturn(snapshot("612.34500000"));

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"symbol": "BNBUSDT", "price": "612.34500000", "fetchedAt": "2026-09-15T12:30:45Z"}
                        """, true));
    }

    @Test
    void latestPriceIsNotFoundWhenNothingStored() throws Exception {
        when(priceQueryService.getLatest()).thenThrow(new PriceDataNotFoundException("No prices stored"));

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No prices stored"));
    }

    @Test
    void averagePriceIsANumber() throws Exception {
        when(priceQueryService.getAveragePrice(new TimeRange(Instant.parse(FROM), Instant.parse(TO))))
                .thenReturn(new BigDecimal("10.2"));

        mockMvc.perform(get("/api/v1/prices/average").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"averagePrice": 10.2}
                        """, true));
    }

    @Test
    void wholeAveragePriceIsNotWrittenInScientificNotation() throws Exception {
        when(priceQueryService.getAveragePrice(any())).thenReturn(new BigDecimal("600.00").stripTrailingZeros());

        mockMvc.perform(get("/api/v1/prices/average").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"averagePrice\":600}"));
    }

    @Test
    void reversedRangeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average").param("from", TO).param("to", FROM))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(priceQueryService);
    }

    @Test
    void malformedDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/average").param("from", "yesterday").param("to", TO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRangeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices/trend").param("from", FROM))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pricesArePaginated() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 2);
        when(priceQueryService.getPrices(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(snapshot("600.10000000"), snapshot("601.20000000")), pageRequest, 5));

        mockMvc.perform(get("/api/v1/prices").param("from", FROM).param("to", TO).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "items": [
                            {"symbol": "BNBUSDT", "price": "600.10000000", "fetchedAt": "2026-09-15T12:30:45Z"},
                            {"symbol": "BNBUSDT", "price": "601.20000000", "fetchedAt": "2026-09-15T12:30:45Z"}
                          ],
                          "page": 0,
                          "size": 2,
                          "totalElements": 5,
                          "hasNext": true
                        }
                        """, true));
    }

    @Test
    void pageSizeAboveLimitIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices").param("from", FROM).param("to", TO)
                        .param("size", String.valueOf(PriceController.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativePageIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/prices").param("from", FROM).param("to", TO).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendResponseUsesSnakeCaseDates() throws Exception {
        when(priceQueryService.getSymbol()).thenReturn("BNBUSDT");
        when(priceQueryService.getTrend(any())).thenReturn(Trend.UPWARD);

        mockMvc.perform(get("/api/v1/prices/trend").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "symbol": "BNBUSDT",
                          "start_date": "2026-09-01T00:00:00Z",
                          "end_date": "2026-09-30T23:59:59Z",
                          "trend": "UPWARD"
                        }
                        """, true));
    }

    @Test
    void trendFromSinglePointIsUnprocessable() throws Exception {
        when(priceQueryService.getTrend(any())).thenThrow(new InsufficientPriceDataException("Not enough"));

        mockMvc.perform(get("/api/v1/prices/trend").param("from", FROM).param("to", TO))
                .andExpect(status().isUnprocessableEntity());
    }

    private static PriceSnapshot snapshot(String price) {
        return new PriceSnapshot("BNBUSDT", new BigDecimal(price), FETCHED_AT.minusSeconds(1), 5, FETCHED_AT);
    }
}
