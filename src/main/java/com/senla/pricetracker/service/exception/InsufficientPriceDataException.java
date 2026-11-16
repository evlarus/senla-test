package com.senla.pricetracker.service.exception;

public class InsufficientPriceDataException extends RuntimeException {

    public InsufficientPriceDataException(String message) {
        super(message);
    }
}
