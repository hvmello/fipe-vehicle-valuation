package com.fipe.valuation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FIPE reference-table entry from {@code GET /references} — {@code {code, month}} (e.g.
 * {@code {"334", "junho/2026"}}). The {@code code} identifies the monthly price table; we track the
 * latest to drive monthly cache rollover. See api-contract.md Part B #1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeReferenceTable(String code, String month) {
}
