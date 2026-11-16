package com.senla.pricetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_snapshot")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, updatable = false)
    private String symbol;

    @Column(name = "price", nullable = false, updatable = false)
    private BigDecimal price;

    @Column(name = "close_time", nullable = false, updatable = false)
    private Instant closeTime;

    @Column(name = "average_window_minutes", nullable = false, updatable = false)
    private int averageWindowMinutes;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt;

    protected PriceSnapshot() {
    }

    public PriceSnapshot(String symbol, BigDecimal price, Instant closeTime, int averageWindowMinutes, Instant fetchedAt) {
        this.symbol = symbol;
        this.price = price;
        this.closeTime = closeTime;
        this.averageWindowMinutes = averageWindowMinutes;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getCloseTime() {
        return closeTime;
    }

    public int getAverageWindowMinutes() {
        return averageWindowMinutes;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
