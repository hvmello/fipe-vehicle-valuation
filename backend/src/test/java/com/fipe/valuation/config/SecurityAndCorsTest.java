package com.fipe.valuation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the hardened transport policy (NFR-7): baseline security response headers on every
 * response, and a narrow CORS contract — configured origins only, the read-only methods this
 * service exposes, an explicit header allow-list (no wildcard), no credentials, and a cached
 * preflight.
 *
 * <p>The CORS contract is asserted on the {@link CorsConfiguration} the application builds, not via
 * a live preflight: under {@code WebTestClient} the server-side request URI arrives without a host,
 * so Spring's {@code isSameOrigin} check throws and rejects every cross-origin exchange — an
 * artifact of the test transport, not of the policy. Asserting the configuration is deterministic
 * and tests exactly what was hardened.
 */
class SecurityAndCorsTest {

    @Nested
    @DisplayName("CORS policy (NFR-7)")
    class CorsPolicy {

        private final CorsConfiguration config =
                WebFluxCorsConfig.corsConfiguration(new FipeProperties(
                        "https://example.test", "", Duration.ofSeconds(5), Duration.ofSeconds(10), 8,
                        new FipeProperties.Cors(List.of("http://localhost:4200")),
                        new FipeProperties.Cache(
                                new FipeProperties.Cache.Catalog(Duration.ofDays(7), 2000),
                                new FipeProperties.Cache.Price(Duration.ofHours(24), 20_000),
                                Duration.ofHours(24))));

        @Test
        @DisplayName("only the configured origins are allowed")
        void restrictsOrigins() {
            assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:4200");
        }

        @Test
        @DisplayName("only safe read methods are allowed")
        void restrictsMethods() {
            assertThat(config.getAllowedMethods()).containsExactly("GET", "OPTIONS");
        }

        @Test
        @DisplayName("headers are an explicit allow-list, never a wildcard")
        void restrictsHeadersNoWildcard() {
            assertThat(config.getAllowedHeaders())
                    .containsExactly(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE)
                    .doesNotContain("*");
        }

        @Test
        @DisplayName("credentials are not allowed and preflight is cached")
        void noCredentialsAndCachedPreflight() {
            assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
            assertThat(config.getMaxAge()).isEqualTo(WebFluxCorsConfig.PREFLIGHT_MAX_AGE.getSeconds());
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebTestClient
    @DisplayName("Security response headers (NFR-7)")
    class SecurityHeaders {

        @Autowired
        private WebTestClient web;

        @Test
        @DisplayName("every response carries the baseline security headers")
        void securityHeadersPresent() {
            // A bad vehicle type short-circuits to 400 before any upstream call — no FIPE stub needed.
            web.get().uri("/api/v1/planes/brands").exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                    .expectHeader().valueEquals("X-Frame-Options", "DENY")
                    .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
        }
    }
}
