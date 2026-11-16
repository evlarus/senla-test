package com.senla.pricetracker.service.exception;

public class PriceDataNotFoundException extends RuntimeException {

    public PriceDataNotFoundException(String message) {
        super(message);
    }
}
