package com.senla.pricetracker.web;

import com.senla.pricetracker.service.exception.InsufficientPriceDataException;
import com.senla.pricetracker.service.exception.InvalidTimeRangeException;
import com.senla.pricetracker.service.exception.PriceDataNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidTimeRangeException.class)
    public ProblemDetail handleInvalidTimeRange(InvalidTimeRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PriceDataNotFoundException.class)
    public ProblemDetail handlePriceDataNotFound(PriceDataNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientPriceDataException.class)
    public ProblemDetail handleInsufficientPriceData(InsufficientPriceDataException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
