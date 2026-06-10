package com.fipe.valuation.client;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.domain.VehicleType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Thin reactive gateway over the FIPE v2 endpoints we consume (#2–#5 in
 * specs/001-vehicle-valuation/api-contract.md Part B). Implementations translate transport errors
 * into {@code FipeNotFoundException}/{@code FipeIntegrationException}.
 */
public interface FipeClient {

    /** FIPE reference tables (months), newest first — used to track the current price table. */
    Flux<FipeReferenceTable> references();

    /** Brands for a vehicle type (FR-7). */
    Flux<FipeReference> brands(VehicleType type);

    /** Models for a brand (FR-8). */
    Flux<FipeReference> models(VehicleType type, String brandId);

    /** Manufacture-year codes for a model (e.g. {@code "1987-1"}). */
    Flux<FipeReference> years(VehicleType type, String brandId, String modelId);

    /** Price detail for a single model-year code. */
    Mono<FipeVehiclePrice> yearPrice(VehicleType type, String brandId, String modelId, String yearCode);
}
