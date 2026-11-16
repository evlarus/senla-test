package com.senla.pricetracker.scheduler;

import com.senla.pricetracker.service.PricePollingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "price-polling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PricePollingScheduler.class);

    private final PricePollingService pricePollingService;

    public PricePollingScheduler(PricePollingService pricePollingService) {
        this.pricePollingService = pricePollingService;
    }

    @Scheduled(fixedDelayString = "${binance.poll-interval}")
    public void pollPrice() {
        try {
            pricePollingService.poll();
        } catch (RuntimeException e) {
            log.error("Unexpected error during price polling", e);
        }
    }
}
