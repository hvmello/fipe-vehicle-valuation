package com.fipe.valuation.domain;

import java.util.List;

/**
 * A vehicle's valuation across its manufacture years, newest first.
 *
 * @param vehicleType    the requested type.
 * @param brand          brand name (from FIPE).
 * @param model          model name (from FIPE).
 * @param fipeCode       FIPE code of the model.
 * @param referenceMonth FIPE reference month of the prices.
 * @param years          year entries, ordered newest-first.
 */
public record VehicleValuation(
        VehicleType vehicleType,
        String brand,
        String model,
        String fipeCode,
        String referenceMonth,
        List<YearValuation> years) {
}
