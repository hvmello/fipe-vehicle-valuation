package com.fipe.valuation.web;

import com.fipe.valuation.client.FipeClient;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.service.VehicleValuationService;
import com.fipe.valuation.web.dto.BrandResponse;
import com.fipe.valuation.web.dto.ModelResponse;
import com.fipe.valuation.web.dto.ValuationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read-only API consumed by the Angular client (api-contract.md Part A). The controller only adapts
 * HTTP to the domain: it resolves the {@link VehicleType} (unknown → 400 via the exception handler),
 * delegates, and maps results to response DTOs.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "FIPE valuation", description = "Brands, models and year-by-year vehicle valuation")
public class FipeController {

    private final FipeClient fipeClient;
    private final VehicleValuationService valuationService;

    public FipeController(FipeClient fipeClient, VehicleValuationService valuationService) {
        this.fipeClient = fipeClient;
        this.valuationService = valuationService;
    }

    @Operation(summary = "List brands for a vehicle type")
    @GetMapping("/{type}/brands")
    public Flux<BrandResponse> brands(@PathVariable String type) {
        VehicleType vehicleType = VehicleType.fromPath(type);
        return fipeClient.brands(vehicleType).map(ref -> BrandResponse.from(vehicleType, ref));
    }

    @Operation(summary = "List models for a brand")
    @GetMapping("/{type}/brands/{brandId}/models")
    public Flux<ModelResponse> models(@PathVariable String type, @PathVariable String brandId) {
        VehicleType vehicleType = VehicleType.fromPath(type);
        return fipeClient.models(vehicleType, brandId).map(ref -> ModelResponse.from(vehicleType, ref));
    }

    @Operation(summary = "Year-by-year valuation with variation vs the previous available year")
    @GetMapping("/{type}/brands/{brandId}/models/{modelId}/valuation")
    public Mono<ValuationResponse> valuation(@PathVariable String type,
                                             @PathVariable String brandId,
                                             @PathVariable String modelId) {
        VehicleType vehicleType = VehicleType.fromPath(type);
        return valuationService.valuate(vehicleType, brandId, modelId).map(ValuationResponse::from);
    }
}
