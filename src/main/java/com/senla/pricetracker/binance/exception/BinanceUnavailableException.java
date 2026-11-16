package com.senla.pricetracker.binance.exception;

public class BinanceUnavailableException extends BinanceApiException {

    public BinanceUnavailableException(String message) {
        super(message);
    }

    public BinanceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
