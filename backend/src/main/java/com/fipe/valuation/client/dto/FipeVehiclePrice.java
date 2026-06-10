package com.fipe.valuation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FIPE price object (schema {@code VehicleDetail}) returned by the year-detail endpoint — the only
 * endpoint that carries {@code vehicleType} (as an integer). All nine fields verified live
 * (specs/001-vehicle-valuation/data-model.md §1).
 *
 * <p>{@code price} is a pt-BR currency string (e.g. {@code "R$ 6.027,00"}); it is parsed to
 * {@code BigDecimal} downstream — never used as a floating-point number.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeVehiclePrice(
        int vehicleType,
        String price,
        String brand,
        String model,
        int modelYear,
        String fuel,
        String codeFipe,
        String referenceMonth,
        String fuelAcronym) {
}
