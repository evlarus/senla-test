package com.senla.pricetracker.web.dto;

import com.senla.pricetracker.domain.PriceSnapshot;
import org.springframework.data.domain.Page;

import java.util.List;

public record PricePageResponse(
        List<PriceResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {

    public static PricePageResponse from(Page<PriceSnapshot> page) {
        return new PricePageResponse(
                page.map(PriceResponse::from).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext());
    }
}
