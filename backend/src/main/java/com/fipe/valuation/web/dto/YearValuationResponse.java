package com.fipe.valuation.web.dto;

import com.fipe.valuation.domain.YearValuation;
import java.math.BigDecimal;

/**
 * One year row in the valuation response (mirrors {@link YearValuation}). Null change fields denote
 * the oldest entry. See api-contract.md Part A §3.
 */
public record YearValuationResponse(
        int year,
        String label,
        String fuel,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        Integer previousYear,
        String previousLabel) {

    public static YearValuationResponse from(YearValuation year) {
        return new YearValuationResponse(year.year(), year.label(), year.fuel(), year.price(),
                year.change(), year.changePercent(), year.previousYear(), year.previousLabel());
    }
}
