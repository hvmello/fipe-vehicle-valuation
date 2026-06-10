package com.fipe.valuation.domain;

import java.math.BigDecimal;

/**
 * One manufacture-year entry in a vehicle's valuation history.
 *
 * <p>{@code change}/{@code changePercent}/{@code previousYear}/{@code previousLabel} are {@code null}
 * for the oldest entry (no prior year to compare). {@code label} is the year, or {@code "0 km"} when
 * {@code year == 32000}. {@code previousLabel} names the compared entry, qualified by fuel when it
 * differs (e.g. {@code "1996 Álcool"}). See specs/001-vehicle-valuation/data-model.md §3/§5.
 *
 * @param year          FIPE model year (32000 for 0 km).
 * @param label         display label ("0 km" when year == 32000, else the year).
 * @param fuel          fuel description (e.g. "Gasolina").
 * @param price         FIPE price, BigDecimal scale 2.
 * @param change        price − previous price (scale 2), or null for the oldest entry.
 * @param changePercent change as a percentage of the previous price (scale 2), or null.
 * @param previousYear  year of the compared entry, or null.
 * @param previousLabel label of the compared entry (fuel-qualified when different), or null.
 */
public record YearValuation(
        int year,
        String label,
        String fuel,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        Integer previousYear,
        String previousLabel) {
}
