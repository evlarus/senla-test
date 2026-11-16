package com.senla.pricetracker.binance.exception;

public class BinanceRequestRejectedException extends BinanceApiException {

    private final int httpStatus;
    private final Integer errorCode;

    public BinanceRequestRejectedException(int httpStatus, Integer errorCode, String details) {
        super("Binance rejected the request (HTTP %d, code %s): %s".formatted(httpStatus, errorCode, details));
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Integer getErrorCode() {
        return errorCode;
    }
}
