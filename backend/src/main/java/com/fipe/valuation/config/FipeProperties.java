package com.fipe.valuation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised configuration for the upstream FIPE API (prefix {@code fipe}).
 *
 * <p>Base URL and credentials follow the primary source documented in
 * {@code specs/001-vehicle-valuation/api-contract.md} (Part B). The subscription token is optional:
 * the public host answers unauthenticated within a rate-limited free tier, so the token is sent
 * only when configured.
 *
 * @param baseUrl           FIPE v2 base URL (default = the fipe.online primary host).
 * @param subscriptionToken optional {@code X-Subscription-Token}; blank = not sent.
 * @param connectTimeout    TCP connect timeout for FIPE calls.
 * @param responseTimeout   per-request response timeout for FIPE calls.
 * @param maxConcurrency    upper bound on concurrent per-year price requests.
 * @param cors              CORS settings for the Angular client.
 * @param cache             Caffeine cache settings for FIPE responses.
 */
@ConfigurationProperties(prefix = "fipe")
@Validated
public record FipeProperties(
        @DefaultValue("https://fipe.parallelum.com.br/api/v2") @NotBlank String baseUrl,
        @DefaultValue("") String subscriptionToken,
        @DefaultValue("5s") @NotNull Duration connectTimeout,
        @DefaultValue("10s") @NotNull Duration responseTimeout,
        @DefaultValue("8") @Positive int maxConcurrency,
        @DefaultValue @Valid Cors cors,
        @DefaultValue @Valid Cache cache) {

    /** @return true when a non-blank subscription token is configured. */
    public boolean hasToken() {
        return subscriptionToken != null && !subscriptionToken.isBlank();
    }

    /**
     * CORS configuration.
     *
     * @param allowedOrigins origins permitted to call our API (default = Angular dev server).
     */
    public record Cors(@DefaultValue("http://localhost:4200") @NotEmpty List<String> allowedOrigins) {
    }

    /**
     * FIPE response cache settings (NFR-4/NFR-8). Two tiers with their own bounds so the hot working
     * set stays resident while cold entries are evicted (Caffeine W-TinyLFU):
     * <ul>
     *   <li><b>catalog</b> — brands/models/years: stable, reference-independent → long TTL.</li>
     *   <li><b>price</b> — monthly: keyed by the current reference table and invalidated when FIPE
     *       rolls over (see {@code CurrentReferenceProvider}).</li>
     * </ul>
     *
     * @param catalog          catalog (brand/model/year) cache settings.
     * @param price            price cache settings.
     * @param referenceRefresh how often to re-check FIPE's current reference table.
     */
    public record Cache(
            @DefaultValue @Valid Catalog catalog,
            @DefaultValue @Valid Price price,
            @DefaultValue("24h") @NotNull Duration referenceRefresh) {

        /** Catalog cache: brand/model/year lists. */
        public record Catalog(
                @DefaultValue("7d") @NotNull Duration ttl,
                @DefaultValue("2000") @Positive long maximumSize) {
        }

        /** Price cache: one entry per (type, brand, model, year, reference). */
        public record Price(
                @DefaultValue("24h") @NotNull Duration ttl,
                @DefaultValue("20000") @Positive long maximumSize) {
        }
    }
}
