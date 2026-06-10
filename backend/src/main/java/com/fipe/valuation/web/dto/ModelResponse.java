package com.fipe.valuation.web.dto;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.domain.VehicleType;

/**
 * Model for a dropdown. Echoes {@code vehicleType} (FR-14); {@code id} is FIPE's {@code code}.
 */
public record ModelResponse(String vehicleType, String id, String name) {

    public static ModelResponse from(VehicleType type, FipeReference reference) {
        return new ModelResponse(type.path(), reference.code(), reference.name());
    }
}
