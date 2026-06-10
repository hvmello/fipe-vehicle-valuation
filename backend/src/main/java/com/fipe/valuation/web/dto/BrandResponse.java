package com.fipe.valuation.web.dto;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.domain.VehicleType;

/**
 * Brand for a dropdown. Echoes {@code vehicleType} (FR-14) since FIPE's list endpoints omit it;
 * {@code id} is FIPE's {@code code}.
 */
public record BrandResponse(String vehicleType, String id, String name) {

    public static BrandResponse from(VehicleType type, FipeReference reference) {
        return new BrandResponse(type.path(), reference.code(), reference.name());
    }
}
