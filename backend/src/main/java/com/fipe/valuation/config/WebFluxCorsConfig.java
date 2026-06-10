package com.fipe.valuation.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Enables CORS for the Angular client (NFR-7). The policy is deliberately narrow: only the
 * configured origins, only the safe read methods this service exposes ({@code GET}/{@code OPTIONS}),
 * and only the request headers a JSON {@code GET} actually needs ({@code Accept},
 * {@code Content-Type}). Credentials are not allowed — the API is public and stateless — so a
 * wildcard origin is never combined with cookies. Preflight results are cached for an hour.
 */
@Configuration
public class WebFluxCorsConfig {

    static final Duration PREFLIGHT_MAX_AGE = Duration.ofHours(1);

    @Bean
    CorsWebFilter corsWebFilter(FipeProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration(properties));
        return new CorsWebFilter(source);
    }

    /**
     * The hardened CORS policy applied to {@code /api/**}. Extracted so the contract (origins,
     * read-only methods, explicit header allow-list, no credentials, cached preflight) can be
     * asserted directly in a test without depending on a live preflight exchange.
     */
    static CorsConfiguration corsConfiguration(FipeProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE));
        config.setAllowCredentials(false);
        config.setMaxAge(PREFLIGHT_MAX_AGE);
        return config;
    }
}
