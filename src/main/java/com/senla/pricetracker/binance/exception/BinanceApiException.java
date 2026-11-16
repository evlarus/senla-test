package com.senla.pricetracker.binance.exception;

public abstract class BinanceApiException extends RuntimeException {

    protected BinanceApiException(String message) {
        super(message);
    }

    protected BinanceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
