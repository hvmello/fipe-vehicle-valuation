package com.fipe.valuation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FIPE {@code {code, name}} pair — the shared shape returned by the brands, models and years
 * endpoints (schema {@code NamedCode}). See specs/001-vehicle-valuation/data-model.md §1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeReference(String code, String name) {
}
