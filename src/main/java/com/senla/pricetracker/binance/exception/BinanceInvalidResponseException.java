package com.senla.pricetracker.binance.exception;

public class BinanceInvalidResponseException extends BinanceApiException {

    public BinanceInvalidResponseException(String message) {
        super(message);
    }

    public BinanceInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
