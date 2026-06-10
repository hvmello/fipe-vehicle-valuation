package com.fipe.valuation.web.dto;

import com.fipe.valuation.domain.VehicleValuation;
import java.util.List;

/**
 * The valuation result returned to the client: vehicle metadata plus the year-by-year history,
 * newest first. {@code currency} is always {@code "BRL"}. See api-contract.md Part A §3.
 */
public record ValuationResponse(
        String vehicleType,
        String brand,
        String model,
        String fipeCode,
        String referenceMonth,
        String currency,
        List<YearValuationResponse> years) {

    private static final String CURRENCY_BRL = "BRL";

    public static ValuationResponse from(VehicleValuation valuation) {
        List<YearValuationResponse> years = valuation.years().stream()
                .map(YearValuationResponse::from)
                .toList();
        return new ValuationResponse(
                valuation.vehicleType().path(),
                valuation.brand(),
                valuation.model(),
                valuation.fipeCode(),
                valuation.referenceMonth(),
                CURRENCY_BRL,
                years);
    }
}
